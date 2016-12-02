package nachos.vm;

import java.util.LinkedList;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

/**
 * A kernel that can support multiple demand-paging user processes.
 */
public class VMKernel extends UserKernel {
    /**
     * Allocate a new VM kernel.
     */
    public VMKernel() {
        super();
    }
    
    /**
     * Initialize this kernel.
     */
    public void initialize(String[] args) {
        super.initialize(args);
        //VMFreePage = UserKernel.freePage;
        
        OpenFile file = ThreadedKernel.fileSystem.open(swapFileName, true);
        victim = 0;
        invertedPT = new invertedData[Machine.processor().getNumPhysPages()];
    }
    
    /**
     * Test this kernel.
     */
    public void selfTest() {
        super.selfTest();
    }
    
    /**
     * Start running user programs.
     */
    public void run() {
        super.run();
    }
    
    //update the inverted table
    public static boolean swapIn(){
        return true;
    }
    public static int pageAllocation(){
        if(freePage.size()!= 0){
            return freePage.removeFirst();
        }
        //Replacement algorithm
        while(invertedPT[victim].entry.used == true){
            invertedPT[victim].entry.used = false;
            victim = (victim + 1) % invertedPT.length;
        }
        
        int toEvict = victim;
        victim = (victim + 1) % invertedPT.length;
        return toEvict;
    }
    
    public static class invertedData {
        VMProcess process;
        TranslationEntry entry;
        boolean pinned;
        public invertedData(VMProcess process, TranslationEntry entry, boolean pinned){
            this.process = process;
            this.entry = entry;
            this.pinned = pinned;
        }
        public TranslationEntry getEntry(){
            return this.entry;
        }
        
    }
    /**
     * Terminate this kernel. Never returns.
     */
    public void terminate() {
        //swapFile.close();
        //ThreadedKernel.fileSystem.remove(swapFile.getName());
        super.terminate();
    }
    
    
    // dummy variables to make javac smarter
    private static VMProcess dummy1 = null;
    
    private static final char dbgVM = 'v';
    //public static LinkedList <Integer> VMFreePage = new LinkedList <Integer>();
    private static LinkedList<Integer> freeSwapPages;
    public static OpenFile swapFile;
    public static String swapFileName = ".TEM";
    public static invertedData[] invertedPT;
    public static int victim; 
}
