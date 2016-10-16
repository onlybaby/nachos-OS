package nachos.threads;

import nachos.machine.*;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>, and multiple
 * threads can be waiting to <i>listen</i>. But there should never be a time
 * when both a speaker and a listener are waiting, because the two threads can
 * be paired off at this point.
 */
public class Communicator {
    /**
     * Allocate a new communicator.
     */
    public Communicator() {
        //we need 3 condition variables with 1 lock
        lock = new Lock();
        speaker = new Condition2(lock);
        listener = new Condition2(lock);
        waiter = new Condition2(lock);
        buffer = null;
    }
    
    /**
     * Wait for a thread to listen through this communicator, and then transfer
     * <i>word</i> to the listener.
     *
     * <p>
     * Does not return until this thread is paired up with a listening thread.
     * Exactly one listener should receive <i>word</i>.
     *
     * @param word the integer to transfer.
     */
    public void speak(int word) {
        lock.acquire(); //use lock to prevent other into critical section
        while (buffer !=null){
            speaker.sleep(); //speaker waits buffer is full
        }
        buffer = word; //fill the buffer
        listener.wake(); //right now buffer is full, then wake up the listener
        waiter.sleep(); //waiter make sure speaker is return after listener
        lock.release(); //give up the critical section
    }
    
    /**
     * Wait for a thread to speak through this communicator, and then return the
     * <i>word</i> that thread passed to <tt>speak()</tt>.
     *
     * @return the integer transferred.
     */
    public int listen() {
        lock.acquire(); //go into the critical section
        while (buffer == null){
            listener.sleep(); //listen waits when buffer is empty
        }
        int temp_buffer = buffer; //store the buffer to return
        buffer = null; //buffer is empty right now
        speaker.wake(); //wake up speaker
        waiter.wake(); //right now speaker can return
        lock.release(); //give up the critical section
        return temp_buffer; //return the buffer
    }
    public static void commTest6() {
        final Communicator com = new Communicator();
        final long times[] = new long[4];
        final int words[] = new int[2];
        KThread speaker1 = new KThread( new Runnable () {
            public void run() {
                com.speak(4);
                times[0] = Machine.timer().getTime();
            }
        });
        speaker1.setName("S1");
        KThread speaker2 = new KThread( new Runnable () {
            public void run() {
                com.speak(7);
                times[1] = Machine.timer().getTime();
            }
        });
        speaker2.setName("S2");
        KThread listener1 = new KThread( new Runnable () {
            public void run() {
                times[2] = Machine.timer().getTime();
                words[0] = com.listen();
            }
        });
        listener1.setName("L1");
        KThread listener2 = new KThread( new Runnable () {
            public void run() {
                times[3] = Machine.timer().getTime();
                words[1] = com.listen();
            }
        });
        listener2.setName("L2");
        
        speaker1.fork(); speaker2.fork(); listener1.fork(); listener2.fork();
        speaker1.join(); speaker2.join(); listener1.join(); listener2.join();
        
        Lib.assertTrue(words[0] == 4, "Didn't listen back spoken word.");
        Lib.assertTrue(words[1] == 7, "Didn't listen back spoken word.");
        Lib.assertTrue(times[0] > times[2], "speak() returned before listen() called.");
        Lib.assertTrue(times[1] > times[3], "speak() returned before listen() called.");
        System.out.println("commTest6 successful!");
    }
    
    // Invoke Communicator.selfTest() from ThreadedKernel.selfTest()
    
    public static void selfTest() {
        // place calls to simpler Communicator tests that you implement here
        
        commTest6();
    }
    
    private Condition2 speaker; //speaker speaks when the buffer is empty-fill up buffer
    private Condition2 listener; //listener listens when buffer is full-consume buffer
    private Condition2 waiter; //waiter make sure speaker return after listener
    
    private Lock lock;
    private Integer buffer;
}
