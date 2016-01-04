import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;

/**
 * The class controller is responsible to set a branch’s initial balance and
 * notify every branch of all other branches in the distributed bank. It also
 * contacts one of the branches to initiate the global snapshot. After some time
 * (long enough for the snapshot algorithm to finish), the controller makes
 * retrieveSnapshot calls to all branches to retrieve their recorded local and
 * channel states.
 * 
 * @author chetan
 *
 */
public class Controller {
	private int total_money;
	private File get_branches;
	private List<BranchID> branch_list;
	private int snapshotnumber=0;
	private int retrive_snap_number;
	
	public Controller() {

	}

	public static void main(String[] args) {

		Controller controller = new Controller();

		if (args.length < 0) {

			System.out.println(Constants.no_args_error);
		}
		
		else {

			try {
				controller.total_money = Integer.parseInt(args[0]);

				controller.get_branches = new File(args[1]);

				controller.init();
			} catch (NumberFormatException nfe) {

				System.out.println(Constants.invalid_bal);
			}
		}
		
	}

	public void init() {

		parse();

		initBranches();
	}

	/**
	 * Parse command line arguments to retrieve branch details of all branches.
	 */
	private void parse() {

		branch_list = new ArrayList<BranchID>();

		try {
			
			Scanner input = new Scanner(get_branches);
			
			while (input.hasNextLine()) {
				
				String line=input.nextLine();
				
				line=line.trim();
				
				String[] details = line.split(" ");

				if(details.length>1) {
		
					BranchID branch = new BranchID();

					branch.name = details[0];

					branch.ip = details[1];

					branch.port = (Integer.parseInt(details[2]));

					branch_list.add(branch);
				}
			}
			
			if(input!=null)
				input.close();
			
		} catch (IOException e) {

			e.printStackTrace();
		} catch (Exception e) {

			e.printStackTrace();
		}

	}

	
	/**
	 * Initializes all branches with initial amount of money and a list of all
	 * other branches included in distributed bank.
	 */
	private void initBranches() {

		int avail_bal = total_money / branch_list.size();

		for (int i = 0; i < branch_list.size(); i++) {

			try {

				TTransport transport = new TSocket(branch_list.get(i).ip, branch_list.get(i).port);

				transport.open();

				TProtocol protocol = new TBinaryProtocol(transport);

				Branch.Client remoteBranch = new Branch.Client(protocol);

				remoteBranch.initBranch(avail_bal, branch_list);

			} catch (TTransportException e) {
				
				e.printStackTrace();
			} catch (SystemException e) {
				
				e.printStackTrace();
			} catch (TException e) {

				e.printStackTrace();
			}
		}
		
		initTimerTask();
	}
	
	/**
	 * Schedules a timer to capture global snapshot of distributed bank at a
	 * particular instance.
	 */
	private void initTimerTask() {
		
		new Timer().scheduleAtFixedRate(new TimerTask() {
			
			@Override
			public void run() {
				// TODO Auto-generated method stub
				
				try {

					snapshotnumber=snapshotnumber+1;
					
					int index=new Random().nextInt(branch_list.size());
					
					TTransport transport = new TSocket(branch_list.get(0).ip, branch_list.get(0).port);

					transport.open();

					TProtocol protocol = new TBinaryProtocol(transport);

					Branch.Client remoteBranch = new Branch.Client(protocol);
					
					System.out.println("\n\nSnapshot initiated at "+branch_list.get(index).name);
					
					remoteBranch.initSnapshot(snapshotnumber);
					
					transport.close();
					
					retrieveSnapShotTimerTask(snapshotnumber);

				} catch (TTransportException e) {
					
					e.printStackTrace();
				} catch (SystemException e) {
					
					e.printStackTrace();
				} catch (TException e) {

					e.printStackTrace();
				}
				
			}
		}, 5*1000,20*1000);
		
	}
	
	
	/**
	 * Schedules a timer to retrieve details of snapshot from all branches of
	 * distributed bank.
	 * 
	 * @param snap_num
	 *            Snapshot number.
	 */
	private void retrieveSnapShotTimerTask(int snap_num) {
		
		retrive_snap_number=snap_num;
		
		new Timer().schedule(new TimerTask() {
			
			@Override
			public void run() {
				// TODO Auto-generated method stub
				
				System.out.println("\n***** Details of Snapshot "+retrive_snap_number+ " *****");
				
				for (int i = 0; i < branch_list.size(); i++) {

					try {

						TTransport transport = new TSocket(branch_list.get(i).ip, branch_list.get(i).port);

						transport.open();

						TProtocol protocol = new TBinaryProtocol(transport);

						Branch.Client remoteBranch = new Branch.Client(protocol);

						LocalSnapshot snapshot=remoteBranch.retrieveSnapshot(retrive_snap_number);
						
						if(snapshot!=null) {
							
							System.out.println(branch_list.get(i).name+" local balance -> "+snapshot.balance);
							
							List<Integer> channelStates= snapshot.messages;
							
							for(int j=0;j<channelStates.size();j++) {
								
								System.out.println("Amount in Transit-> "+channelStates.get(j));
							}
						}
						
						transport.close();

					} catch (TTransportException e) {
						
						e.printStackTrace();
					} catch (SystemException e) {
						
						e.printStackTrace();
					} catch (TException e) {

						e.printStackTrace();
					}
				}
			}
		}, 5*1000);	
	}
}
