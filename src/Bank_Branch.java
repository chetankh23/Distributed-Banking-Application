
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;


/**
 * The class Bank_Branch spawns a multi-threaded Thrift server and also thread
 * that sends random amount of money to other branches at any interval of time.
 * 
 * @author chetan
 *
 */
public class Bank_Branch {

	String local_branch_name;
	int local_port;
	Branch_Handler branchHandler;
	Timer timer;
	BranchID myBranchID;
	List<BranchID> tempList;
	int sendingDelay;
	
	public Bank_Branch() {
		// TODO Auto-generated constructor stub

	}

	public static void main(String[] args) {

		Bank_Branch myBranch = new Bank_Branch();

		if (args.length < 0) {

			System.out.println(Constants.no_args_error);
		
		} else {

			try {

				myBranch.local_branch_name = args[0];

				myBranch.local_port = Integer.parseInt(args[1]);

				myBranch.start();

			} catch (NumberFormatException nfe) {

				System.out.println(Constants.invalid_port);
			}
		}
	}
	
	
	/**
	 * Starts Branch server to serve incoming RPC requests.
	 */
	public void start() {

		try {

			TServerSocket serverTransport = new TServerSocket(local_port);

			branchHandler=new Branch_Handler(local_branch_name,local_port, Bank_Branch.this);
			
			Branch.Processor<Branch_Handler> processor = new Branch.Processor<Branch_Handler>(branchHandler);

			// creates a multi-threaded thrift server to serve RPC requests
			TServer server = new TThreadPoolServer(new TThreadPoolServer.Args(serverTransport).processor(processor));

			System.out.println("Branch started on port "+local_port);

			server.serve();
			
		} catch (TTransportException e) {

			e.printStackTrace();
		}
	}
	
	
	/**
	 * This method initiates money transfer operation to all other branches
	 * scheduled at fixed rate.
	 */
	public void initiateMoneyTransfer() {
		
		timer=new Timer();
		
		tempList=new ArrayList<BranchID>();
		
		if(branchHandler.branch_list!=null && branchHandler.branch_list.size()>0) {
		
			for(int i=0;i<branchHandler.branch_list.size();i++) {
				
				if(branchHandler.branch_list.get(i).name.equals(local_branch_name)) {
					
					myBranchID=branchHandler.branch_list.get(i);
					
					continue;
				}
				
				tempList.add(branchHandler.branch_list.get(i));
			}
			
			
		}
		
		timer.scheduleAtFixedRate(new SendMoneyTask(tempList), 2*1000, 1000);
	}
	
	
	/**
	 * The class SendMoneyTask includes code to send random amount of money to a
	 * random branch. The run() method of this class is invoked each time the
	 * timer for sending operation kicks off.
	 * 
	 * @author chetan
	 *
	 */
	class SendMoneyTask extends TimerTask 
	{
		List<BranchID> senderList;
		Random randomGenerator=new Random();
		
		
		public SendMoneyTask(List<BranchID> senderList) {
			
			this.senderList=senderList;
			
			randomGenerator=new Random();
		}
		
		public void run() {
			
			int index=randomGenerator.nextInt(senderList.size());
			
			try {
					TTransport transport = new TSocket(senderList.get(index).ip, senderList.get(index).port);
					
					transport.open();

					TProtocol protocol = new TBinaryProtocol(transport);

					Branch.Client remoteBranch = new Branch.Client(protocol);
					
					TransferMessage transfer=new TransferMessage();
					
					int amount=(int)(Math.random()*(5-1))+1;
					
					amount=(int)(branchHandler.initialBalance*((float) amount)/100.0f);
					
					transfer.setAmount(amount);
					
					transfer.setOrig_branchId(myBranchID);
					
					branchHandler.setBranchAmount(amount, true);
					
					remoteBranch.transferMoney(transfer);
					
			} catch (TTransportException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (SystemException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (TException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	
	/**
	 * Pause the money transfer operation.
	 */
	public void pauseMoneyTransfer() {
		
		if(timer!=null)
			timer.cancel();
		
		timer=null;
	}
	
	
	/**
	 * Resumes the money transfer operation and schedules it at fixed rate. 
	 */
	public void resumeMoneyTransfer() {
		
		if(timer!=null)
			timer.cancel();
		
		timer=new Timer();
		
		timer.scheduleAtFixedRate(new SendMoneyTask(tempList), 2*1000, 1000);
	}
	
	/**
	 * This method includes code to sends marker messages to all other branches
	 * in order to capture snapshot of the branch when initiated.
	 * 
	 * @param snapshot_num
	 *            Snapshot number
	 */
	public void sendMarkerMessages(int snapshot_num) {
		
		for(int i=0;i<tempList.size();i++) {
			
			branchHandler.snapshotData.put(tempList.get(i), 0);
			
			branchHandler.branchRecordedState.put(tempList.get(i), true);
		}
		
		
		for(int i=0;i<tempList.size();i++) {
			
			try {
				
				TTransport transport = new TSocket(tempList.get(i).ip, tempList.get(i).port);
			
				transport.open();

				TProtocol protocol = new TBinaryProtocol(transport);

				Branch.Client remoteBranch = new Branch.Client(protocol);
				
				remoteBranch.Marker(myBranchID, snapshot_num);
				
				transport.close();
			
			} catch (TTransportException e) {
				// TODO Auto-generated catch block
				//e.printStackTrace();
			} catch (TException e) {
				// TODO Auto-generated catch block
				//e.printStackTrace();
			}
		}	
	}
}
