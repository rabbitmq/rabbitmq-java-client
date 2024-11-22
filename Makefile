MVN ?= mvn
MVN_FLAGS ?=

ifndef DEPS_DIR
ifneq ($(wildcard ../../UMBRELLA.md),)
DEPS_DIR = ..
else
DEPS_DIR = deps
endif
endif

MVN_FLAGS += -Ddeps.dir="$(abspath $(DEPS_DIR))"

.PHONY: all deps tests clean distclean

all: deps
	$(MVN) $(MVN_FLAGS) compile

deps: $(DEPS_DIR)/rabbitmq_codegen
	@:

dist: clean
	$(MVN) $(MVN_FLAGS) -DskipTests=true -Dmaven.javadoc.failOnError=false package javadoc:javadoc

$(DEPS_DIR)/rabbitmq_codegen:
	git clone -n --depth=1 --filter=tree:0 https://github.com/rabbitmq/rabbitmq-server.git $(DEPS_DIR)/rabbitmq-server
	git -C $(DEPS_DIR)/rabbitmq-server sparse-checkout set --no-cone deps/rabbitmq_codegen
	git -C $(DEPS_DIR)/rabbitmq-server checkout
	cp -r $(DEPS_DIR)/rabbitmq-server/deps/rabbitmq_codegen "$@"
	rm -rf $(DEPS_DIR)/rabbitmq-server

tests: deps
	$(MVN) $(MVN_FLAGS) verify

deploy:
	$(MVN) $(MVN_FLAGS) deploy

clean:
	$(MVN) $(MVN_FLAGS) clean

distclean: clean
	$(MAKE) -C $(DEPS_DIR)/rabbitmq_codegen clean

.PHONY: cluster-other-node

cluster-other-node:
	$(exec_verbose) $(RABBITMQCTL) -n $(OTHER_NODE) stop_app
	$(verbose) $(RABBITMQCTL) -n $(OTHER_NODE) reset
	$(verbose) $(RABBITMQCTL) -n $(OTHER_NODE) join_cluster \
	  $(if $(MAIN_NODE),$(MAIN_NODE),$(RABBITMQ_NODENAME)@$$(hostname -s))
	$(verbose) $(RABBITMQCTL) -n $(OTHER_NODE) start_app
