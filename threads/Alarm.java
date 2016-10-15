package nachos.threads;

import nachos.machine.*;
import java.util.*;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 * 
	 * <p>
	 * <b>Note</b>: Nachos will not function correctly with more than one alarm.
	 */
	public class Pair {
		KThread thread;
		long time_to_wakeUp;
		public Pair (KThread thread, long time){
			this.thread = thread;
			this.time_to_wakeUp = time;
		}
		public long getTime(){
			return this.time_to_wakeUp; 
		}
		public KThread getThread(){
			return this.thread;
		}
	}

	private ArrayList<Pair> waitQueue = new ArrayList<Pair>();
	//Lock waitLock = new Lock();
	//private Iterator<Pair> it = waitQueue.iterator();
	public Alarm() {
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() {
				timerInterrupt();
			}
		});
	}

	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread that
	 * should be run.
	 */
	public void timerInterrupt() {
		boolean status = Machine.interrupt().disable();
		for(int i= 0; i < waitQueue.size(); i++){
			if(waitQueue.get(i).getTime() < Machine.timer().getTime()){
				waitQueue.get(i).getThread().ready();
				waitQueue.remove(i);
				
			}
		}
		Machine.interrupt().restore(status);
		KThread.currentThread().yield();
		
	}

	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks, waking it up
	 * in the timer interrupt handler. The thread must be woken up (placed in
	 * the scheduler ready set) during the first timer interrupt where
	 * 
	 * <p>
	 * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
	 * 
	 * @param x the minimum number of clock ticks to wait.
	 * 
	 * @see nachos.machine.Timer#getTime()
	 */
	public void waitUntil(long x) {
		// for now, cheat just to get something working (busy waiting is bad)
		boolean status = Machine.interrupt().disable();
		long wakeTime = Machine.timer().getTime() + x;
		Pair temp = new Pair(KThread.currentThread(), wakeTime);
		this.waitQueue.add(temp);
		KThread.currentThread().sleep();
		Machine.interrupt().restore(status);
		
	}
}
