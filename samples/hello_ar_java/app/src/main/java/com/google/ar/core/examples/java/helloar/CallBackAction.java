package com.google.ar.core.examples.java.helloar;

interface CallBackAction {
  void updateResultPointCloud(ServerResponsePoints serverResponsePoints);
  void setServerPoints(ServerPoints serverPoints);
}
