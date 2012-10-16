// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: query.proto

#ifndef PROTOBUF_query_2eproto__INCLUDED
#define PROTOBUF_query_2eproto__INCLUDED

#include <string>

#include <google/protobuf/stubs/common.h>

#if GOOGLE_PROTOBUF_VERSION < 2004000
#error This file was generated by a newer version of protoc which is
#error incompatible with your Protocol Buffer headers.  Please update
#error your headers.
#endif
#if 2004001 < GOOGLE_PROTOBUF_MIN_PROTOC_VERSION
#error This file was generated by an older version of protoc which is
#error incompatible with your Protocol Buffer headers.  Please
#error regenerate this file with a newer version of protoc.
#endif

#include <google/protobuf/generated_message_util.h>
#include <google/protobuf/repeated_field.h>
#include <google/protobuf/extension_set.h>
#include <google/protobuf/generated_message_reflection.h>
// @@protoc_insertion_point(includes)

// Internal implementation detail -- do not call these.
void  protobuf_AddDesc_query_2eproto();
void protobuf_AssignDesc_query_2eproto();
void protobuf_ShutdownFile_query_2eproto();

class Query;

// ===================================================================

class Query : public ::google::protobuf::Message {
 public:
  Query();
  virtual ~Query();
  
  Query(const Query& from);
  
  inline Query& operator=(const Query& from) {
    CopyFrom(from);
    return *this;
  }
  
  inline const ::google::protobuf::UnknownFieldSet& unknown_fields() const {
    return _unknown_fields_;
  }
  
  inline ::google::protobuf::UnknownFieldSet* mutable_unknown_fields() {
    return &_unknown_fields_;
  }
  
  static const ::google::protobuf::Descriptor* descriptor();
  static const Query& default_instance();
  
  void Swap(Query* other);
  
  // implements Message ----------------------------------------------
  
  Query* New() const;
  void CopyFrom(const ::google::protobuf::Message& from);
  void MergeFrom(const ::google::protobuf::Message& from);
  void CopyFrom(const Query& from);
  void MergeFrom(const Query& from);
  void Clear();
  bool IsInitialized() const;
  
  int ByteSize() const;
  bool MergePartialFromCodedStream(
      ::google::protobuf::io::CodedInputStream* input);
  void SerializeWithCachedSizes(
      ::google::protobuf::io::CodedOutputStream* output) const;
  ::google::protobuf::uint8* SerializeWithCachedSizesToArray(::google::protobuf::uint8* output) const;
  int GetCachedSize() const { return _cached_size_; }
  private:
  void SharedCtor();
  void SharedDtor();
  void SetCachedSize(int size) const;
  public:
  
  ::google::protobuf::Metadata GetMetadata() const;
  
  // nested types ----------------------------------------------------
  
  // accessors -------------------------------------------------------
  
  // required bytes query = 1;
  inline bool has_query() const;
  inline void clear_query();
  static const int kQueryFieldNumber = 1;
  inline const ::std::string& query() const;
  inline void set_query(const ::std::string& value);
  inline void set_query(const char* value);
  inline void set_query(const void* value, size_t size);
  inline ::std::string* mutable_query();
  inline ::std::string* release_query();
  
  // @@protoc_insertion_point(class_scope:Query)
 private:
  inline void set_has_query();
  inline void clear_has_query();
  
  ::google::protobuf::UnknownFieldSet _unknown_fields_;
  
  ::std::string* query_;
  
  mutable int _cached_size_;
  ::google::protobuf::uint32 _has_bits_[(1 + 31) / 32];
  
  friend void  protobuf_AddDesc_query_2eproto();
  friend void protobuf_AssignDesc_query_2eproto();
  friend void protobuf_ShutdownFile_query_2eproto();
  
  void InitAsDefaultInstance();
  static Query* default_instance_;
};
// ===================================================================


// ===================================================================

// Query

// required bytes query = 1;
inline bool Query::has_query() const {
  return (_has_bits_[0] & 0x00000001u) != 0;
}
inline void Query::set_has_query() {
  _has_bits_[0] |= 0x00000001u;
}
inline void Query::clear_has_query() {
  _has_bits_[0] &= ~0x00000001u;
}
inline void Query::clear_query() {
  if (query_ != &::google::protobuf::internal::kEmptyString) {
    query_->clear();
  }
  clear_has_query();
}
inline const ::std::string& Query::query() const {
  return *query_;
}
inline void Query::set_query(const ::std::string& value) {
  set_has_query();
  if (query_ == &::google::protobuf::internal::kEmptyString) {
    query_ = new ::std::string;
  }
  query_->assign(value);
}
inline void Query::set_query(const char* value) {
  set_has_query();
  if (query_ == &::google::protobuf::internal::kEmptyString) {
    query_ = new ::std::string;
  }
  query_->assign(value);
}
inline void Query::set_query(const void* value, size_t size) {
  set_has_query();
  if (query_ == &::google::protobuf::internal::kEmptyString) {
    query_ = new ::std::string;
  }
  query_->assign(reinterpret_cast<const char*>(value), size);
}
inline ::std::string* Query::mutable_query() {
  set_has_query();
  if (query_ == &::google::protobuf::internal::kEmptyString) {
    query_ = new ::std::string;
  }
  return query_;
}
inline ::std::string* Query::release_query() {
  clear_has_query();
  if (query_ == &::google::protobuf::internal::kEmptyString) {
    return NULL;
  } else {
    ::std::string* temp = query_;
    query_ = const_cast< ::std::string*>(&::google::protobuf::internal::kEmptyString);
    return temp;
  }
}


// @@protoc_insertion_point(namespace_scope)

#ifndef SWIG
namespace google {
namespace protobuf {


}  // namespace google
}  // namespace protobuf
#endif  // SWIG

// @@protoc_insertion_point(global_scope)

#endif  // PROTOBUF_query_2eproto__INCLUDED