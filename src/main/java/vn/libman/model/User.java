package vn.libman.model;

public record User(
  int id,
  String username,
  String fullName,
  String role
) implements java.io.Serializable {}
