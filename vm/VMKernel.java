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
        
        swapFile = ThreadedKernel.fileSystem.open(swapFileName, true);
        freeSwapList = new LinkedList<Integer>();
        //swapFile = new OpenFile(file, swapFileName);
        victim = 0;
        numPinned = 0;
        spn = 0;
        invertedPT = new invertedData[Machine.processor().getNumPhysPages()];
        swapFileLock = new Lock();
        swapListLock = new Lock();
        pinnedLock = new Lock();
        spnLock = new Lock();
        
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
    public static boolean swapIn(int vpn, VMProcess workprocess, int ppn){
        
        //get the SPN
        int tempSPN = workprocess.pageTable[vpn].vpn;
        if(tempSPN < 0 || ppn < 0) return false;
        //read from swapFile
        byte[] memory = Machine.processor().getMemory();
        swapFileLock.acquire();
        swapFile.read(tempSPN*Processor.pageSize, memory, ppn*Processor.pageSize, Processor.pageSize);
        swapFileLock.release();
        //add the spn to the freeSwapList
        swapListLock.acquire();
        freeSwapList.add(tempSPN);
        swapListLock.release();
   	    
        //put ppn into translation entry and set to valid bit to true
        proLock.acquire();
        workprocess.pageTable[vpn].ppn = ppn;
        //workprocess.pageTable[vpn].vpn = vpn;
   	    workprocess.pageTable[vpn].valid = true;
   	    proLock.release();
   	    
        return true;
    }
    
    public static boolean swapOut(int ppn){
        //if(ppn >= 0 || ppn)
        int tempSPN = 0;
        if(VMKernel.freeSwapList.size() == 0){
            spnLock.acquire();
            tempSPN = ++spn;
            spnLock.release();
        } else {
            swapListLock.acquire();
            tempSPN = VMKernel.freeSwapList.removeFirst();
            swapListLock.release();
        }
        
        invertedPT[ppn].entry.vpn = tempSPN;
        //invertedPT[ppn].entry.valid = false;
        byte[] memory = Machine.processor().getMemory();
        swapFileLock.acquire();
        swapFile.write(tempSPN*Processor.pageSize, memory, ppn*Processor.pageSize, Processor.pageSize);
        swapFileLock.release();
        return true;
    }
    public static int pageAllocation(){
        if(freePage.size()!= 0){
            return freePage.removeFirst();
        }
        
        if(numPinned == invertedPT.length){
            unpinnedPage.sleep(); //all pages pinned
        }
        //Replacement algorithm
        while(invertedPT[victim].entry.used == true ||invertedPT[victim].pinned ==true){
            invertedPT[victim].entry.used = false;
            victim = (victim + 1) % invertedPT.length;
        }
        
        int toEvict = victim;
        victim = (victim + 1) % invertedPT.length;
        if(invertedPT[toEvict].entry.dirty==true)
            swapOut(toEvict);
        invertedPT[toEvict].entry.valid = false;
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
        swapFile.close();
        ThreadedKernel.fileSystem.remove(swapFile.getName());
        super.terminate();
    }
    
    
    // dummy variables to make javac smarter
    private static VMProcess dummy1 = null;
    
    private static final char dbgVM = 'v';
    //public static LinkedList <Integer> VMFreePage = new LinkedList <Integer>();
    public static int spn;
    public static Lock spnLock;
    public static OpenFile swapFile;
    public static Lock swapFileLock;
    public static String swapFileName = ".TEM";
    public static invertedData[] invertedPT;
    public static Lock invertedLock;
    public static int victim;
    public static LinkedList<Integer> freeSwapList;
    public static Lock swapListLock;
    public static int numPinned;
    public static Lock pinnedLock;
    public static Condition unpinnedPage;
}
