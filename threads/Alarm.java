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
    
    
    public Alarm() {
        
        waitQueue = new LinkedList <Pair>();
        
        Machine.timer().setInterruptHandler(new Runnable() {
            public void run() {
                timerInterrupt();
            }
        });
    }
    
    public class Pair{
        private KThread l;
        private long r;
        
        public Pair(KThread l, long r){
            this.l = l;
            this.r = r;
        }
        
        private KThread getL(){
            return l;
        }
        private long getR(){
            return r;
        }
        
    }
    /**
     * The timer interrupt handler. This is called by the machine's timer
     * periodically (approximately every 500 clock ticks). Causes the current
     * thread to yield, forcing a context switch if there is another thread that
     * should be run.
     */
    public void timerInterrupt() {
        boolean intStatus = Machine.interrupt().disable();
        Iterator <Pair> i = waitQueue.iterator();
        while(i.hasNext()){
            Pair temp = i.next();
            KThread currThread = temp.getL();
            long wakeTime = temp.getR();
            if(wakeTime <= Machine.timer().getTime()){
                currThread.ready();
                i.remove();
            }
        }
        Machine.interrupt().restore(intStatus);
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
        boolean intStatus = Machine.interrupt().disable();
        long wakeTime = Machine.timer().getTime() + x;
        Pair temp = new Pair(KThread.currentThread(), wakeTime);
        waitQueue.add(temp);
        KThread.currentThread().sleep();
        Machine.interrupt().restore(intStatus);
    }
    
    private LinkedList <Pair> waitQueue;
    
}
