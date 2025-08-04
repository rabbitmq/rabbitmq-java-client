package com.rabbitmq.client.impl;

interface HeartbeatSender {

  HeartbeatSender NO_OP =
      new HeartbeatSender() {
        @Override
        public void setHeartbeat(int heartbeat) {}

        @Override
        public void signalActivity() {}

        @Override
        public void shutdown() {}
      };

  void setHeartbeat(int heartbeat);

  void signalActivity();

  void shutdown();
}
