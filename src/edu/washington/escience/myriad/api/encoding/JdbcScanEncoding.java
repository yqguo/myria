package edu.washington.escience.myriad.api.encoding;

import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response.Status;

import com.google.common.collect.ImmutableList;

import edu.washington.escience.myriad.RelationKey;
import edu.washington.escience.myriad.Schema;
import edu.washington.escience.myriad.accessmethod.JdbcInfo;
import edu.washington.escience.myriad.api.MyriaApiException;
import edu.washington.escience.myriad.coordinator.catalog.CatalogException;
import edu.washington.escience.myriad.operator.JdbcQueryScan;
import edu.washington.escience.myriad.operator.Operator;
import edu.washington.escience.myriad.parallel.Server;

/**
 * Scan a table in DBMS via JDBC
 * 
 * @author Shumo Chu <chushumo@cs.washington.edu>
 * 
 */
public class JdbcScanEncoding extends OperatorEncoding<JdbcQueryScan> {

  /** The name of the relation to be scanned. */
  public RelationKey relationKey;

  /** JDBC info of the database where the relation is from */
  public JdbcInfo jdbcInfo;

  private static final List<String> requiredArguments = ImmutableList.of("relationKey", "jdbcInfo");

  @Override
  public void connect(final Operator current, final Map<String, Operator> operators) {
    /* Do nothing; no children. */
  }

  @Override
  public JdbcQueryScan construct(final Server server) {
    Schema schema;
    try {
      schema = server.getSchema(relationKey);
    } catch (final CatalogException e) {
      throw new MyriaApiException(Status.INTERNAL_SERVER_ERROR, e);
    }
    if (schema == null) {
      throw new MyriaApiException(Status.BAD_REQUEST, "Specified relation " + relationKey.toString(jdbcInfo.getDbms())
          + " does not exist.");
    }
    return new JdbcQueryScan(jdbcInfo, relationKey, schema);
  }

  @Override
  protected List<String> getRequiredArguments() {
    return requiredArguments;
  }
}