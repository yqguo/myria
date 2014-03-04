package edu.washington.escience.myria.parallel;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;

import edu.washington.escience.myria.MyriaConstants;
import edu.washington.escience.myria.TupleBatch;
import edu.washington.escience.myria.operator.RootOperator;
import edu.washington.escience.myria.operator.StreamingState;
import edu.washington.escience.myria.operator.TupleSource;
import edu.washington.escience.myria.parallel.ipc.FlowControlBagInputBuffer;
import edu.washington.escience.myria.parallel.ipc.StreamInputBuffer;
import edu.washington.escience.myria.parallel.ipc.StreamOutputChannel;
import edu.washington.escience.myria.util.DateTimeUtils;

/**
 * A {@link WorkerQueryPartition} is a partition of a query plan at a single worker.
 * */
public class WorkerQueryPartition extends QueryPartitionBase {

  /**
   * logger.
   * */
  private static final Logger LOGGER = LoggerFactory.getLogger(WorkerQueryPartition.class);

  /**
   * The owner {@link Worker}.
   * */
  private final Worker ownerWorker;

  /**
   * Number of finished tasks.
   * */
  private final AtomicInteger numFinishedTasks;

  /**
   * Store the current pause future if the query is in pause, otherwise null.
   * */
  private final AtomicReference<QueryFuture> pauseFuture = new AtomicReference<QueryFuture>(null);

  /**
   * The future listener for processing the complete events of the execution of all the query's tasks.
   * */
  private final TaskFutureListener taskExecutionListener = new TaskFutureListener() {

    @Override
    public void operationComplete(final TaskFuture future) throws Exception {
      QuerySubTreeTask drivingTask = future.getTask();
      int currentNumFinished = numFinishedTasks.incrementAndGet();

      getExecutionFuture().setProgress(1, currentNumFinished, getTasks().size());
      Throwable failureReason = future.getCause();
      if (!future.isSuccess()) {
        getFailTasks().add(drivingTask);
        if (!(failureReason instanceof QueryKilledException)) {
          // The task is a failure, not killed.
          if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("got a failed task, root op = " + drivingTask.getRootOp().getOpName() + ", cause ",
                failureReason);
          }
          for (QuerySubTreeTask t : getTasks()) {
            // kill other tasks
            t.kill();
          }
        }
      }

      if (currentNumFinished >= getTasks().size()) {
        getExecutionStatistics().markQueryEnd();
        if (LOGGER.isInfoEnabled()) {
          LOGGER.info("Query #" + getQueryID() + " executed for "
              + DateTimeUtils.nanoElapseToHumanReadable(getExecutionStatistics().getQueryExecutionElapse()));
        }
        if (getFailTasks().isEmpty()) {
          getExecutionFuture().setSuccess();
        } else {
          Throwable existingCause = getExecutionFuture().getCause();
          Throwable newCause = getFailTasks().peek().getExecutionFuture().getCause();
          if (existingCause == null) {
            getExecutionFuture().setFailure(newCause);
          } else {
            existingCause.addSuppressed(newCause);
          }
        }
      } else {
        if (LOGGER.isDebugEnabled()) {
          LOGGER.debug("New finished task: {}. {} remain.", drivingTask, (getTasks().size() - currentNumFinished));
        }
      }
    }

  };

  @Override
  protected ExecutorService getTaskExecutor(final RootOperator root) {
    return ownerWorker.getQueryExecutor();
  }

  @Override
  protected StreamInputBuffer<TupleBatch> getInputBuffer(final RootOperator root, final Consumer c) {
    return new FlowControlBagInputBuffer<TupleBatch>(getIPCPool(), c.getInputChannelIDs(getIPCPool().getMyIPCID()),
        ownerWorker.getInputBufferCapacity(), ownerWorker.getInputBufferRecoverTrigger(), getIPCPool());
  }

  /**
   * @param plan the plan of this query partition.
   * @param queryID the id of the query.
   * @param ownerWorker the worker on which this query partition is going to run
   * */
  public WorkerQueryPartition(final SingleQueryPlanWithArgs plan, final long queryID, final Worker ownerWorker) {
    super(plan, queryID, ownerWorker.getIPCConnectionPool());
    numFinishedTasks = new AtomicInteger(0);
    this.ownerWorker = ownerWorker;
    createInitialTasks();
    for (final QuerySubTreeTask t : getTasks()) {
      t.getExecutionFuture().addListener(taskExecutionListener);
    }
  }

  @Override
  public final void init(final DefaultQueryFuture initFuture) {
    final Set<QuerySubTreeTask> initialTasks = getTasks();
    for (final QuerySubTreeTask t : initialTasks) {
      try {
        initTask(t);
      } catch (final Throwable e) {
        initFuture.setFailure(e);
      }
    }
    // safe to just setSuccess because if the init is already failed, the setSuccess won't change the result
    initFuture.setSuccess();
  }

  /**
   * initialize a task.
   * 
   * @param t the task
   * */
  private void initTask(final QuerySubTreeTask t) {
    TaskResourceManager resourceManager =
        new TaskResourceManager(ownerWorker.getIPCConnectionPool(), t, ownerWorker.getQueryExecutionMode());
    ImmutableMap.Builder<String, Object> b = ImmutableMap.builder();
    t.init(resourceManager, b.putAll(ownerWorker.getExecEnvVars()).build());
  }

  @Override
  public final void startExecution(final QueryFuture executionFuture) {
    if (LOGGER.isInfoEnabled()) {
      LOGGER.info("Query : " + getQueryID() + " start processing.");
    }

    if (isProfilingMode()) {
      PROFILING_LOGGER.info("[{}#{}][{}@{}][{}][{}]:set time", MyriaConstants.EXEC_ENV_VAR_QUERY_ID, getQueryID(),
          "startTimeInMS", "0", System.currentTimeMillis(), 0);
      PROFILING_LOGGER.info("[{}#{}][{}@{}][{}][{}]:set time", MyriaConstants.EXEC_ENV_VAR_QUERY_ID, getQueryID(),
          "startTimeInNS", "0", System.nanoTime(), 0);
    }

    getExecutionStatistics().markQueryStart();
    for (QuerySubTreeTask t : getTasks()) {
      t.execute();
    }
  }

  @Override
  public final QueryFuture pause() {
    final QueryFuture pauseF = new DefaultQueryFuture(this, true);
    while (!pauseFuture.compareAndSet(null, pauseF)) {
      QueryFuture current = pauseFuture.get();
      if (current != null) {
        // already paused by some other threads, do not do the actual pause
        return current;
      }
    }
    return pauseF;
  }

  @Override
  public final QueryFuture resume() {
    QueryFuture pf = pauseFuture.getAndSet(null);
    DefaultQueryFuture rf = new DefaultQueryFuture(this, true);

    if (pf == null) {
      // query is not in pause, return success directly.
      rf.setSuccess();
      return rf;
    }
    // TODO do the resume stuff
    return rf;
  }

  @Override
  public final void kill(final DefaultQueryFuture future) {
    for (QuerySubTreeTask task : getTasks()) {
      task.kill();
    }
  }

  @Override
  public final boolean isPaused() {
    return pauseFuture.get() != null;
  }

  /**
   * add a recovery task for the failed worker.
   * 
   * @param workerId the id of the failed worker.
   */
  public void addRecoveryTasks(final int workerId) {
    List<RootOperator> recoveryTasks = new ArrayList<RootOperator>();
    for (QuerySubTreeTask task : getTasks()) {
      if (task.getRootOp() instanceof Producer) {
        if (LOGGER.isTraceEnabled()) {
          LOGGER.trace("adding recovery task for " + task.getRootOp().getOpName());
        }
        List<StreamingState> buffers = ((Producer) task.getRootOp()).getTriedToSendTuples();
        List<Integer> indices = ((Producer) task.getRootOp()).getChannelIndicesOfAWorker(workerId);
        StreamOutputChannel<TupleBatch>[] channels = ((Producer) task.getRootOp()).getChannels();
        for (int i = 0; i < indices.size(); ++i) {
          int j = indices.get(i);
          /* buffers.get(j) might be an empty List<TupleBatch>, so need to set its schema explicitly. */
          TupleSource scan = new TupleSource(buffers.get(j).exportState(), buffers.get(j).getSchema());
          scan.setOpName("tuplesource for " + task.getRootOp().getOpName() + channels[j].getID());
          RecoverProducer rp =
              new RecoverProducer(scan, ExchangePairID.fromExisting(channels[j].getID().getStreamID()), channels[j]
                  .getID().getRemoteID(), (Producer) task.getRootOp(), j);
          rp.setOpName("recProducer_for_" + task.getRootOp().getOpName());
          recoveryTasks.add(rp);
          scan.setFragmentId(0 - recoveryTasks.size());
          rp.setFragmentId(0 - recoveryTasks.size());
        }
      }
    }
    final List<QuerySubTreeTask> list = new ArrayList<QuerySubTreeTask>();
    for (RootOperator cp : recoveryTasks) {
      QuerySubTreeTask recoveryTask = createTask(cp).getExecutionFuture().addListener(taskExecutionListener).getTask();
      list.add(recoveryTask);
    }
    Thread t = new Thread() {
      @Override
      public void run() {
        while (true) {
          if (ownerWorker.getIPCConnectionPool().isRemoteAlive(workerId)) {
            /* waiting for ADD_WORKER to be received */
            for (QuerySubTreeTask task : list) {
              initTask(task);
              /* input might be null but we still need it to run */
              task.notifyNewInput();
            }
            break;
          }
          try {
            Thread.sleep(MyriaConstants.SHORT_WAITING_INTERVAL_100_MS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        }
      }
    };
    t.start();
  }

  /**
   * @return the owner worker
   */
  public Worker getOwnerWorker() {
    return ownerWorker;
  }
}
