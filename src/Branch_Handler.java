import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.thrift.TException;

/**
 * The class Branch_Handler acts as a receiver for a particular branch. It
 * updates the local balance of branch on receiving a particular amount from
 * other branches. As the local balance will be read and written by multiple
 * threads, the balance variable is protected using synchronization method.
 * 
 * @author chetan
 *
 */
public class Branch_Handler implements Branch.Iface {

	public String local_branch_name;
	public List<BranchID> branch_list;
	public int local_balance, local_branch_port,initialBalance;
	Bank_Branch main_branch;
	private final Object lock=new Object();

	public HashMap<BranchID, Integer> snapshotData;
	public HashMap<BranchID, Boolean> branchRecordedState;
	public HashMap<Integer, LocalSnapshot> branchSnapshot;
	
	private boolean isInitiator=false;
	private boolean startRecording=false;
	
	public int snapshot_number;
	
	public int snapshotBalance;
	
	
	private int num_calls=0;
	
	// Constructor of Branch_Handler class.
	public Branch_Handler(String local_branch_name,int local_branch_port, Bank_Branch main_branch) {

		this.local_branch_name=local_branch_name;
		
		this.local_branch_port=local_branch_port;
		
		this.main_branch=main_branch;
	}
	
	/**
	 * Initialize local balance of branch with balance given. This method is
	 * called by Controller to initialize a branch. It also provides a list of
	 * other branches included in the distributed system.
	 */
	@Override
	public void initBranch(int balance, List<BranchID> all_branches) throws SystemException, TException {
		
		initialBalance=balance;
		
		local_balance = balance;

		branch_list = all_branches;
		
		branchSnapshot=new HashMap<>();
		
		main_branch.initiateMoneyTransfer();
		
	}

	
	/**
	 * The method transferMoney is invoked by other branches to send money.
	 * Sending of money is carried out using object of TransferMessage class
	 * which includes branch details of sender and also amount sent. A branch on
	 * receiving message updates its local balance with the amount received.
	 */
	@Override
	public void transferMoney(TransferMessage message) throws SystemException, TException {
		
		setBranchAmount(message.amount, false);
		
		if(startRecording) {
			
			for(BranchID key : branchRecordedState.keySet()) {
				
				if(key.name.equals(message.orig_branchId.name) && branchRecordedState.get(key)==true) {
					
					snapshotData.put(key, message.amount);
				}
			}
			
		}
	}

	
	/**
	 * The method initSnapshot() is invoked by the Controller to initialize the
	 * global snapshot algorithm. A branch on upon receiving this call, records
	 * its own local balance and sends out Marker messages to all other branches
	 * by calling the Marker method on them. To identify multiple snapshots, the
	 * controller passes in a snapshot_num to this call, and all the marker
	 * messages should include this snapshot_num
	 */
	@Override
	public void initSnapshot(int snapshot_num) throws SystemException, TException {

		snapshotData=new HashMap<>();
		
		branchRecordedState=new HashMap<>();
		
		isInitiator=true;
		
		startRecording=true;
		
		//num_calls++;
		
		main_branch.pauseMoneyTransfer();
		
		snapshotBalance=local_balance;
		
		main_branch.sendMarkerMessages(snapshot_num);
	}


	/**
	 * Given the sending branch’s BranchID and snapshot_num, the receiving
	 * branch does the following:
	 * 
	 * 1. If this is the first Marker message with the snapshot_num, the
	 * receiving branch records its own local balance, records the state of the
	 * incoming channel from the sender to itself as empty, starts recording on
	 * other incoming channels, and sends out Marker messages to all branches
	 * except itself.
	 * 
	 * 2. Otherwise, the receiving branch records the state of the incoming
	 * channel as the sequence of money transfers that arrived between when it
	 * recorded its local state and when it received the Marker.
	 */
	@Override
	public void Marker(BranchID branchId, int snapshot_num) throws SystemException, TException {

		snapshot_number=snapshot_num;
		
		if (isInitiator == false && startRecording == false) {
			
			Branch_Handler.this.snapshotData = new HashMap<>();

			branchRecordedState = new HashMap<>();

			num_calls++;

			isInitiator = false;

			startRecording = true;

			main_branch.pauseMoneyTransfer();

			snapshotBalance = local_balance;
			
			main_branch.sendMarkerMessages(snapshot_number);
		}
		
		else if(isInitiator==true && startRecording==true) {
			
			num_calls++;
			
			if(num_calls==(branch_list.size()-1)) {
				
				startRecording=false;
				
				num_calls=0;
				
				storeSnapshotData(snapshot_number);
				
				main_branch.resumeMoneyTransfer();

			}
			else {
				
					for(BranchID key : branchRecordedState.keySet()) {
					
						if(key.name.equals(branchId.name))
							branchRecordedState.put(key, false);
					}
				
			}
		}
		
		else if(isInitiator==false && startRecording==true) {
			
			num_calls++ ;
			
			if(num_calls==(branch_list.size()-1)) {
				
				startRecording=false;
				
				num_calls=0;
				
				storeSnapshotData(snapshot_number);
				
				main_branch.resumeMoneyTransfer();
			}
			else {
				
					for(BranchID key : branchRecordedState.keySet()) {
					
						if(key.name.equals(branchId.name))
							branchRecordedState.put(key, false);
					}
				
			}
		}
	}

	
	/**
	 * Maintains a list of snapshot data based on snapshot number in order to
	 * return to Controller.
	 * 
	 * @param snapshot_number
	 *            Snapshot number.
	 */
	private void storeSnapshotData(int snapshot_number) {

		LocalSnapshot localSnapshot = new LocalSnapshot();

		localSnapshot.balance = snapshotBalance;

		for (BranchID key : snapshotData.keySet()) {

			localSnapshot.addToMessages(snapshotData.get(key));
		}
		
		branchSnapshot.put(snapshot_number, localSnapshot);
		
		snapshotBalance=0;
		
		snapshotData=null;
		
		branchRecordedState=null;
	}

	
	/**
	 * Given the snapshot_num that uniquely identifies a snapshot, a branch
	 * retrieves its recorded local and channel states and return to the
	 * controller.
	 */
	@Override
	public LocalSnapshot retrieveSnapshot(int snapshot_num) throws SystemException, TException {
		
		LocalSnapshot localSnapshot = branchSnapshot.get(snapshot_num);
		
		return localSnapshot;
	}
	
	/**
	 * Updates the local balance of branch with given amount.
	 * 
	 * @param amt
	 *            Amount to be added/deducted in local balance.
	 * @param flag
	 *            If false, amount is added else deducted.
	 */
	public void setBranchAmount(int amt,boolean flag) {
		
		synchronized (lock) {
			
			if(!flag)
				local_balance=local_balance+amt;
			else
				local_balance=local_balance-amt;
		}
	}
}
