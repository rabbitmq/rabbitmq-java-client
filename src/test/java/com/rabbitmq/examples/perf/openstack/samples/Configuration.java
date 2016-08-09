package com.rabbitmq.examples.perf.openstack.samples;

public class Configuration {
	
	//-------------------------------------
	//AMQP messages sizes in Bytes
	//Note: VN: Virtual Network, VM: Virtual Machine
	//compute node published heartbeats (0 VN 0 VM): exchange originated
	public static final int RPC_NOVA_OA_MEAN_SIZE = 1890;
	public static final int RPC_NOVA_OCAV_MEAN_SIZE = 1890;
	public static final int RPC_NOVA_SII_MEAN_SIZE = 1000;
	public static final int RPC_NEUTRON_RS_MEAN_SIZE = 1530;
	
	//compute node consumed heartbeats: queue originated
	public static final int RPC_NOVA_REPLY_MEAN_SIZE = 640;
	public static final int RPC_NEUTRON_REPLY_MEAN_SIZE = 200;
	
	private static final int NBR_OF_VARIABLES = 6;
	
	//additional size when resources are deployed (1 VN 1 VM): exchange
	public static final int RPC_NOVA_OA_ADD_SIZE = 110;
	public static final int RPC_NOVA_OCAV_ADD_SIZE = 110;
	public static final int RPC_NOVA_SII_ADD_SIZE = 210;
	public static final int RPC_NEUTRON_RS_ADD_SIZE = 0;

	//additional size of queue originated messages
	public static final int RPC_NOVA_REPLY_ADD_SIZE = 480;
	public static final int RPC_NEUTRON_REPLY_ADD_SIZE = 40;
	
	//-------------------------------------
	//POP (Point of Presence; i.e. Datacenter) simulation variables
	//For example: the number of compute nodes in a POP.
	private int nbr_cpun;
	private int nbr_vn;
	private int nbr_vm_cpun;
//	private int nbr_vm_vn;
//	private int nbr_cross_vn;
	
	public Configuration(int nbr_cpu_nodes_per_pop, int nbr_vn_per_pop,
			int nbr_vm_per_cpu_node, int nbr_vm_per_vn, int nbr_cross_pop_vn) {
		
		nbr_cpun = nbr_cpu_nodes_per_pop;
		nbr_vn = nbr_vn_per_pop;
		nbr_vm_cpun = nbr_vm_per_cpu_node;
//		nbr_vm_vn = nbr_vm_per_vn;
//		nbr_cross_vn = nbr_cross_pop_vn;
		
	}
	
	public int[] getPopConfigMatrix() {
		int[] config = new int[NBR_OF_VARIABLES];
		config[0] = RPC_NOVA_OA_MEAN_SIZE + RPC_NOVA_OA_ADD_SIZE * nbr_vm_cpun;
		config[1] = RPC_NOVA_OCAV_MEAN_SIZE + RPC_NOVA_OCAV_ADD_SIZE * nbr_vm_cpun; 
		config[2] = RPC_NOVA_SII_MEAN_SIZE + RPC_NOVA_SII_ADD_SIZE * nbr_vm_cpun;
		config[3] = RPC_NEUTRON_RS_MEAN_SIZE + RPC_NEUTRON_RS_ADD_SIZE * nbr_vn;
		config[4] = RPC_NOVA_REPLY_MEAN_SIZE + RPC_NOVA_REPLY_ADD_SIZE * nbr_vm_cpun;
		config[5] = RPC_NEUTRON_REPLY_MEAN_SIZE + RPC_NEUTRON_REPLY_ADD_SIZE * nbr_vn;
		
		for (int i = 0; i < config.length; i++) {
			config[i] = config[i] * nbr_cpun; 
		}
		
		return config;
	}
	
}
