package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {
	/**
	 * Allocate a new process.
	 */
	public VMProcess() {
		super();
		this.lastVPN = 0;
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
		super.saveState();
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		super.restoreState();
	}

	/**
	 * Initializes page tables for this process so that the executable can be
	 * demand-paged.
	 * 
	 * @return <tt>true</tt> if successful.
	 */
	protected boolean loadSections() {
		UserKernel.memLock.acquire(); //require a lock when we do memory allocation

        if (numPages > UserKernel.freePage.size()) {
            UserKernel.memLock.release(); //release the lock and return false if there is not enough physical memory to allocate
            coff.close();
            Lib.debug(dbgProcess, "\tinsufficient physical memory");
            return false;
        }
        
        coffSectionNum = new int[numPages];
        coffPin =  new int[numPages];
        pageTable = new TranslationEntry[numPages]; //create a page table with the size numPage which we need
        
        for (int i = 0; i < numPages; i++){
            //int ppn = UserKernel.freePage.removeFirst(); //get the vpn from the physical memory
            pageTable[i] = new TranslationEntry(i, i, false, false, false, false); //translate vpn to ppn with translationEntry
        }
        
        UserKernel.memLock.release(); //release the lock after we allocate the memory
        //int lastVPN = 0;
        // load sections
        for (int s = 0; s < coff.getNumSections(); s++) {
            CoffSection section = coff.getSection(s);
            Lib.debug(dbgProcess, "\tinitializing " + section.getName()
                      + " section (" + section.getLength() + " pages)");
            
            for (int i = 0; i < section.getLength(); i++) {
                int vpn = section.getFirstVPN() + i;
                if(pageTable[vpn] == null) return false;
                pageTable[vpn].vpn = i;
                //lastVPN = vpn;
                coffPin[vpn] = 1;
                coffSectionNum[vpn] = s;
                pageTable[vpn].readOnly = section.isReadOnly(); //set read only bit to each entry in page table
            }
        }
        //System.out.println("here~~~~~~~~~~~~~~");

        return true;
	}
	
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
        Lib.assertTrue(offset >= 0 && length >= 0
                       && offset + length <= data.length);
        
        byte[] memory = Machine.processor().getMemory();
        
        int amount = 0;
        int checkVPN = Processor.pageFromAddress(vaddr);
        if(pageTable[checkVPN].valid == false){
            	handlePageFault(checkVPN);
            } 
        while (length >0){
            //get the vpn from virtual address
            int vpn = Processor.pageFromAddress(vaddr);
            //check if vpn is valid, if vpn is smaller than 0 or bigger than the pageTable length
            //then it's not valid
            if(vpn<0||vpn>=pageTable.length)
                break;
            //pageTable[vpn].used = true;
            //get the offset from virtual address
            int offSet = Processor.offsetFromAddress(vaddr);
            // get ppn by accessing pageTale according to vpn index
            int ppn = pageTable[vpn].ppn;
            //where to start reading in physical memory
            int paddr = Processor.pageSize*ppn +offSet;
            //available space left for each page
            int off = Processor.pageSize - offSet;
            int actualRead = 0; // successful amount written in each page
            //if there is enough space left to read in
            if(length < off){
                actualRead = length;
                //if there is not enough space left to read in
            }else {
                actualRead = off;
            }
            
            System.arraycopy(memory, paddr, data, offset, actualRead);
            //update corresponding data
            length -= actualRead;
            vaddr += actualRead;
            offset += actualRead;
            amount += actualRead;
        }
        
        return amount;
    }
	
 	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
	//System.out.println("here~~~~~~~~~~~~~~");
        Lib.assertTrue(offset >= 0 && length >= 0
                       && offset + length <= data.length);
        
        byte[] memory = Machine.processor().getMemory();
        
        int amount = 0;
        int checkVPN = Processor.pageFromAddress(vaddr);
        if(pageTable[checkVPN].valid == false){
            	handlePageFault(checkVPN);
            } 
        while (length >0){
            //get the vpn from virtual address
            int vpn = Processor.pageFromAddress(vaddr);
            //check if vpn is valid, if vpn is smaller than 0 or bigger than the pageTable length
            //then it's not valid
            if(vpn<0||vpn>=pageTable.length)
                break;
            pageTable[vpn].dirty = true;//set the dirty bit to true
            //pageTable[vpn].used = true;
            //get the offset from virtual address
            int offSet = Processor.offsetFromAddress(vaddr);
            //get ppn by accessing pageTable at index of vpn
            int ppn = pageTable[vpn].ppn;
            int paddr = Processor.pageSize*ppn +offSet;
            //available spage left in each page
            int off = Processor.pageSize - offSet;
            int actualRead = 0; // successful amount written in each page
            //if there is enough space left to read in
            if(length < off){
                actualRead = length;
                //if there is not enough space left to read in
            }else {
                actualRead = off;
            }
            
            System.arraycopy(data, offset, memory, paddr, actualRead);
            length -= actualRead;
            vaddr += actualRead;
            offset += actualRead;
            amount += actualRead;
        }
        
        return amount;
    }
	public void handlePageFault(int faultVPN){
		if(pageTable[faultVPN].dirty == false){
			for (int s = 0; s < coff.getNumSections(); s++) {
				CoffSection section = coff.getSection(s);     
		        Lib.debug(dbgProcess, "\tinitializing " + section.getName()
		                      + " section (" + section.getLength() + " pages)");       
		        for (int i = 0; i < section.getLength(); i++) {
		        	int vpn = section.getFirstVPN() + i;
		        //	UserKernel.proLock.acquire();
		        	//System.out.println("faultVPN is: " + faultVPN + " vpn is : " + vpn);
		            if(faultVPN == vpn){
		            	if(UserKernel.freePage.size()>= 0){
		            		int ppn = UserKernel.freePage.removeFirst();
		            		if(pageTable[faultVPN] == null) return;
		            		//System.out.println(ppn);
		            		pageTable[faultVPN].ppn = ppn;
		            		section.loadPage(i, ppn);
		            		pageTable[faultVPN].valid = true;
		            		pageTable[faultVPN].dirty = true;
		            		//UserKernel.proLock.release();
		            		return;
		            	}
		            }
		        }
		     }
			/*if(coffPin[faultVPN] == 1){
				int ppn = UserKernel.freePage.removeFirst();
				int coffSectNum = coffSectionNum[faultVPN];
				CoffSection section = coff.getSection(coffSectNum);
				int num = faultVPN - section.getFirstVPN();
				section.loadPage(num, ppn);
				pageTable[faultVPN].ppn = ppn;
			}*/
			int ppn = UserKernel.freePage.removeFirst();
			byte[] memory = Machine.processor().getMemory();
			byte[] buffer = new byte[pageSize];
			System.arraycopy(buffer, 0, memory, pageTable[faultVPN].ppn*pageSize, pageSize);
			pageTable[faultVPN].dirty = true;
			pageTable[faultVPN].valid = true;
		}
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		super.unloadSections();
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
		case Processor.exceptionPageFault: 
			int vaddress = processor.readRegister(Processor.regBadVAddr);
			int vpn = Processor.pageFromAddress(vaddress);
			handlePageFault(vpn);
			break;
		default:
			super.handleException(cause);
			break;
		}
	}

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';
	private int[] coffPage;

	private static final char dbgVM = 'v';
	private static int lastVPN;
	private int[] coffSectionNum;
	private int[] coffPin;
	//private Lock VMPLock;
	
}
