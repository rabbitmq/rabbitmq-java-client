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

deps: $(DEPS_DIR)/rabbit $(DEPS_DIR)/rabbitmq_ct_helpers
	@:

dist: clean
	$(MVN) $(MVN_FLAGS) -DskipTests=true -Dmaven.javadoc.failOnError=false package javadoc:javadoc

$(DEPS_DIR)/rabbit:
	git clone https://github.com/rabbitmq/rabbitmq-server.git $@
	$(MAKE) -C $@ fetch-deps DEPS_DIR="$(abspath $(DEPS_DIR))"

$(DEPS_DIR)/rabbitmq_ct_helpers:
	git clone https://github.com/rabbitmq/rabbitmq-ct-helpers.git "$@"

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
