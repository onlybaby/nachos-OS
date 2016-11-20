package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

import java.io.EOFException;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Encapsulates the state of a user process that is not contained in its user
 * thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed.
 *
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 *
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
    /**
     * Allocate a new process.
     */
    public UserProcess() {
        /*
         int numPhysPages = Machine.processor().getNumPhysPages();
         pageTable = new TranslationEntry[numPhysPages];
         for (int i = 0; i < numPhysPages; i++)
         pageTable[i] = new TranslationEntry(i, i, true, false, false, false);
         */
        
        fileTable = new OpenFile [MAXFILE];
        fileTable[0] = UserKernel.console.openForReading();
        fileTable[1] = UserKernel.console.openForWriting();
        this.pid = UserKernel.processID++;
        
        for (int i = 2; i < MAXFILE; i++){
            fileTable[i] = null;
        }
    }
    
    /**
     * Allocate and return a new process of the correct class. The class name is
     * specified by the <tt>nachos.conf</tt> key
     * <tt>Kernel.processClassName</tt>.
     *
     * @return a new process of the correct class.
     */
    public static UserProcess newUserProcess() {
        String name = Machine.getProcessClassName ();
        
        // If Lib.constructObject is used, it quickly runs out
        // of file descriptors and throws an exception in
        // createClassLoader.  Hack around it by hard-coding
        // creating new processes of the appropriate type.
        
        if (name.equals ("nachos.userprog.UserProcess")) {
            return new UserProcess ();
        } else if (name.equals ("nachos.vm.VMProcess")) {
            return new VMProcess ();
        } else {
            return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
        }
    }
    
    /**
     * Execute the specified program with the specified arguments. Attempts to
     * load the program, and then forks a thread to run it.
     *
     * @param name the name of the file containing the executable.
     * @param args the arguments to pass to the executable.
     * @return <tt>true</tt> if the program was successfully executed.
     */
    public boolean execute(String name, String[] args) {
        if (!load(name, args))
            return false;
        
        UserKernel.runningCount++;
        new UThread(this).setName(name).fork();
        
        return true;
    }
    
    /**
     * Save the state of this process in preparation for a context switch.
     * Called by <tt>UThread.saveState()</tt>.
     */
    public void saveState() {
    }
    
    /**
     * Restore the state of this process after a context switch. Called by
     * <tt>UThread.restoreState()</tt>.
     */
    public void restoreState() {
        Machine.processor().setPageTable(pageTable);
    }
    
    /**
     * Read a null-terminated string from this process's virtual memory. Read at
     * most <tt>maxLength + 1</tt> bytes from the specified address, search for
     * the null terminator, and convert it to a <tt>java.lang.String</tt>,
     * without including the null terminator. If no null terminator is found,
     * returns <tt>null</tt>.
     *
     * @param vaddr the starting virtual address of the null-terminated string.
     * @param maxLength the maximum number of characters in the string, not
     * including the null terminator.
     * @return the string read, or <tt>null</tt> if no null terminator was
     * found.
     */
    public String readVirtualMemoryString(int vaddr, int maxLength) {
        Lib.assertTrue(maxLength >= 0);
        
        byte[] bytes = new byte[maxLength + 1];
        
        int bytesRead = readVirtualMemory(vaddr, bytes);
        
        for (int length = 0; length < bytesRead; length++) {
            if (bytes[length] == 0)
                return new String(bytes, 0, length);
        }
        
        return null;
    }
    
    /**
     * Transfer data from this process's virtual memory to all of the specified
     * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param vaddr the first byte of virtual memory to read.
     * @param data the array where the data will be stored.
     * @return the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data) {
        return readVirtualMemory(vaddr, data, 0, data.length);
    }
    
    /**
     * Transfer data from this process's virtual memory to the specified array.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no data
     * could be copied).
     *
     * @param vaddr the first byte of virtual memory to read.
     * @param data the array where the data will be stored.
     * @param offset the first byte to write in the array.
     * @param length the number of bytes to transfer from virtual memory to the
     * array.
     * @return the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
        Lib.assertTrue(offset >= 0 && length >= 0
                       && offset + length <= data.length);
        
        byte[] memory = Machine.processor().getMemory();
        
        int amount = 0;
        
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
    
    /**
     * Transfer all data from the specified array to this process's virtual
     * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param vaddr the first byte of virtual memory to write.
     * @param data the array containing the data to transfer.
     * @return the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data) {
        return writeVirtualMemory(vaddr, data, 0, data.length);
    }
    
    /**
     * Transfer data from the specified array to this process's virtual memory.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no data
     * could be copied).
     *
     * @param vaddr the first byte of virtual memory to write.
     * @param data the array containing the data to transfer.
     * @param offset the first byte to transfer from the array.
     * @param length the number of bytes to transfer from the array to virtual
     * memory.
     * @return the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
        Lib.assertTrue(offset >= 0 && length >= 0
                       && offset + length <= data.length);
        
        byte[] memory = Machine.processor().getMemory();
        
        int amount = 0;
        
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
    
    /**
     * Load the executable with the specified name into this process, and
     * prepare to pass it the specified arguments. Opens the executable, reads
     * its header information, and copies sections and arguments into this
     * process's virtual memory.
     *
     * @param name the name of the file containing the executable.
     * @param args the arguments to pass to the executable.
     * @return <tt>true</tt> if the executable was successfully loaded.
     */
    private boolean load(String name, String[] args) {
        Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");
        
        OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
        if (executable == null) {
            Lib.debug(dbgProcess, "\topen failed");
            return false;
        }
        
        try {
            coff = new Coff(executable);
        }
        catch (EOFException e) {
            executable.close();
            Lib.debug(dbgProcess, "\tcoff load failed");
            return false;
        }
        
        // make sure the sections are contiguous and start at page 0
        numPages = 0;
        for (int s = 0; s < coff.getNumSections(); s++) {
            CoffSection section = coff.getSection(s);
            if (section.getFirstVPN() != numPages) {
                coff.close();
                Lib.debug(dbgProcess, "\tfragmented executable");
                return false;
            }
            numPages += section.getLength();
        }
        
        // make sure the argv array will fit in one page
        byte[][] argv = new byte[args.length][];
        int argsSize = 0;
        for (int i = 0; i < args.length; i++) {
            argv[i] = args[i].getBytes();
            // 4 bytes for argv[] pointer; then string plus one for null byte
            argsSize += 4 + argv[i].length + 1;
        }
        if (argsSize > pageSize) {
            coff.close();
            Lib.debug(dbgProcess, "\targuments too long");
            return false;
        }
        
        // program counter initially points at the program entry point
        initialPC = coff.getEntryPoint();
        
        // next comes the stack; stack pointer initially points to top of it
        numPages += stackPages;
        initialSP = numPages * pageSize;
        
        // and finally reserve 1 page for arguments
        numPages++;
        
        if (!loadSections())
            return false;
        
        // store arguments in last page
        int entryOffset = (numPages - 1) * pageSize;
        int stringOffset = entryOffset + args.length * 4;
        
        this.argc = args.length;
        this.argv = entryOffset;
        
        for (int i = 0; i < argv.length; i++) {
            byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
            Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
            entryOffset += 4;
            Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
            stringOffset += argv[i].length;
            Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
            stringOffset += 1;
        }
        
        return true;
    }
    
    /**
     * Allocates memory for this process, and loads the COFF sections into
     * memory. If this returns successfully, the process will definitely be run
     * (this is the last step in process initialization that can fail).
     *
     * @return <tt>true</tt> if the sections were successfully loaded.
     */
    protected boolean loadSections() {
        
        UserKernel.memLock.acquire(); //require a lock when we do memory allocation
        
        if (numPages > UserKernel.freePage.size()) {
            UserKernel.memLock.release(); //release the lock and return false if there is not enough physical memory to allocate
            coff.close();
            Lib.debug(dbgProcess, "\tinsufficient physical memory");
            return false;
        }
        
        pageTable = new TranslationEntry[numPages]; //create a page table with the size numPage which we need
        
        for (int i = 0; i < numPages; i++){
            int ppn = UserKernel.freePage.removeFirst(); //get the vpn from the physical memory
            pageTable[i] = new TranslationEntry(i, ppn, true, false, false, false); //translate vpn to ppn with translationEntry
        }
        
        UserKernel.memLock.release(); //release the lock after we allocate the memory
        // load sections
        for (int s = 0; s < coff.getNumSections(); s++) {
            CoffSection section = coff.getSection(s);
            
            Lib.debug(dbgProcess, "\tinitializing " + section.getName()
                      + " section (" + section.getLength() + " pages)");
            
            for (int i = 0; i < section.getLength(); i++) {
                int vpn = section.getFirstVPN() + i;
                
                pageTable[vpn].readOnly = section.isReadOnly(); //set read only bit to each entry in page table
                section.loadPage(i, pageTable[vpn].ppn);
            }
        }
        
        return true;
    }
    
    /**
     * Release any resources allocated by <tt>loadSections()</tt>.
     */
    protected void unloadSections() {
        for (int i = 0; i <pageTable.length; i++)
            UserKernel.freePage.add(pageTable[i].ppn); //release all the physical page from physical memory
    }
    
    /**
     * Initialize the processor's registers in preparation for running the
     * program loaded into this process. Set the PC register to point at the
     * start function, set the stack pointer register to point at the top of the
     * stack, set the A0 and A1 registers to argc and argv, respectively, and
     * initialize all other registers to 0.
     */
    public void initRegisters() {
        Processor processor = Machine.processor();
        
        // by default, everything's 0
        for (int i = 0; i < processor.numUserRegisters; i++)
            processor.writeRegister(i, 0);
        
        // initialize PC and SP according
        processor.writeRegister(Processor.regPC, initialPC);
        processor.writeRegister(Processor.regSP, initialSP);
        
        // initialize the first two argument registers to argc and argv
        processor.writeRegister(Processor.regA0, argc);
        processor.writeRegister(Processor.regA1, argv);
    }
    
    /**
     * Handle the halt() system call.
     */
    private int handleHalt() {
        if(this.pid != 0) return -1;
        Machine.halt();
        
        Lib.assertNotReached("Machine.halt() did not halt machine!");
        return 0;
    }
    
    /**
     * Handle the exit() system call.
     */
    private int handleExit(int status) {
        Machine.autoGrader().finishingCurrentProcess(status);
        for(int i=0; i<16; i++){
        	handleClose(i);
        }
        UserKernel.memLock.acquire();
        unloadSections();
        UserKernel.memLock.release();
        coff.close();
        UserKernel.proLock.acquire();
        
        if(parentProcess!= null){
		System.out.println("here3");
        	int temp = Integer.MIN_VALUE;
        	if(abnormalExitStatus == 0){
        		temp = status;
        	}
		System.out.println("temp is :" + temp);
		System.out.println("pid is:" + this.pid);
        	parentProcess.exitStatusMap.put(this.pid, temp);
		System.out.println("size is:" + exitStatusMap.size());
        	parentProcess.childCV.wake();
        }
        
        /*if(!UserKernel.runningQueue.isEmpty()){
        	UserKernel.runningQueue.removeFirst();
        }*/
        if(--UserKernel.runningCount ==0){
        	Kernel.kernel.terminate();
        }
        /*
        int size = UserKernel.runningQueue.size();
        if(size == 0){
        	Kernel.kernel.terminate();
        }
        */
        UserKernel.proLock.release();
        KThread.finish();
        return 0;
    }
    
    private int handleJoin(int childID, int status){
    	int joinStatus = -1;
    	if(childrenSet.contains(childID)){
    		UserKernel.proLock.acquire();
    		boolean childExitNormally = exitStatusMap.containsKey(childID);
		System.out.println("size is: " + exitStatusMap.size());
		System.out.println("boolean is : " + childExitNormally);
    		while(!exitStatusMap.containsKey(childID)){
    			childCV.sleep();
    		}
		System.out.println("where");
    		int temp = exitStatusMap.get(childID);
    		if(temp!= Integer.MIN_VALUE){
    			joinStatus = 1;
    		}else{
    			joinStatus = 0;
    			writeVirtualMemory(status,Lib.bytesFromInt(temp));
    		}
    	}
    	return joinStatus;
    }
    
    private int handleCreate(int VMaddr){
        String name = readVirtualMemoryString(VMaddr, 256); //get the name from VM address
        if(name == null) return -1; //check the filename we want to create, if no name, fail
        int fd = -1;				//make a file descriptor
        
        for(int i = 0; i <MAXFILE; i++){ //check is there a free page
            if (fileTable[i] == null) {	 //if there is a free one, go to next step
                fd =i;
                break;
            }
        }
        
        if(fd == -1) return -1; //return -1 if no free page
        
        OpenFile temp = ThreadedKernel.fileSystem.open(name, true); //to create the file
        
        if (temp == null)  return -1; //file counldn't be created
        
        fileTable[fd] = temp; //copy the file to the file table
        
        return fd;
    }
    
    private int handleOpen(int VMaddr){
        String name = readVirtualMemoryString(VMaddr, 256); //get the name from VM address
        if(name == null) return -1; //check the filename we want to create, if no name, fail
        int fd = -1;				//make a file descriptor
        
        for(int i = 0; i <MAXFILE; i++){ //check is there a free page
            if (fileTable[i] == null) {	 //if there is a free one, go to next step
                fd =i;
                break;
            }
        }
        
        if(fd == -1) return -1; //return -1 if no free page
        
        OpenFile temp = ThreadedKernel.fileSystem.open(name, false); //to open the file
        
        if (temp == null)  return -1; //file counldn't be opened
        
        fileTable[fd] = temp; //copy the file to the file table
        
        return fd;
    }
    
    private int handleRead(int fd, int VMaddrBuffer, int length){
        
        int maxBufferSize = 1024;  //page size
        byte[] tempBuffer = new byte[maxBufferSize]; //temp buffer to store the contents from file
        int count = 0;		//total bytes read
        if (fd >= MAXFILE || fd < 0 || length < 0) return -1; //return -1 if the paras are incorrect
        
        OpenFile temp = fileTable[fd];	//to find the correct page
        if (temp == null) return -1;	//if page not find return -1
        
        while (length > 0){				//every time read 1024 bytes or length bytes
            int length_or_1024 = 0;
            if (length > maxBufferSize ) {
                length_or_1024 = maxBufferSize; //transfer 1024 bytes if length is greater than 1024
            }else{
                length_or_1024 = length;   //transfer length bytes if length is less than 1024
            }
            
            int actualRead = temp.read(tempBuffer, 0, length_or_1024); //read file to buffer
            if (actualRead <= 0) return -1; //if fail return -1
            
            actualRead = writeVirtualMemory(VMaddrBuffer, tempBuffer, 0, actualRead); //write the buffer to VM
            
            length -= actualRead;			//update length
            VMaddrBuffer += actualRead;		//update VM address
            count += actualRead;			//update the total bytes read
            
            if (actualRead < length_or_1024) break; //end of file
            
        }
        
        return count;
    }
    
    private int handleWrite(int fd, int VMaddrBuffer, int length){
        int maxBufferSize = 1024;  //page size
        byte[] tempBuffer = new byte[maxBufferSize]; //temp buffer to store the contents from file
        int count = 0;		//total bytes read
        if (fd >= MAXFILE || fd < 0 || length < 0) return -1; //return -1 if the paras are incorrect
        
        OpenFile temp = fileTable[fd];	//to find the correct page
        if (temp == null) return -1;	//if page not find return -1
        
        while (length > 0){				//every time read 1024 bytes or length bytes
            int length_or_1024 = 0;
            if (length > maxBufferSize ) {
                length_or_1024 = maxBufferSize; //transfer 1024 bytes if length is greater than 1024
            }else{
                length_or_1024 = length;   //transfer length bytes if length is less than 1024
            }
            
            int actualRead = readVirtualMemory(VMaddrBuffer, tempBuffer, 0, length_or_1024); //read VM to buffer
            if (actualRead <= 0) return -1; //if fail return -1
            
            actualRead = temp.write(tempBuffer, 0, actualRead); //read from buffer to file
            
            length -= actualRead;			//update length
            VMaddrBuffer += actualRead;		//update VM address
            count += actualRead;			//update the total bytes read
            
            if (actualRead < length_or_1024) break; //end of file
            
        }
        
        return count;
    }
    
    private int handleClose(int fd){
        if (fd>=16 ||fd <0) return -1;
        OpenFile temp = fileTable[fd];
        if(temp == null) return -1;
        temp.close();
        fileTable[fd] = null;
        return 0;
    }
    
    private int handleUnlink(int VMaddr){
        String name = readVirtualMemoryString(VMaddr, 256); //get the name from VM address
        if (name == null) return -1;
        if (ThreadedKernel.fileSystem.remove(name)) return 0;
        
        return -1;
    }
    private int handleExec(int file, int argc, int vaddrArgv){
    	//get the Read coffName from virtual address
    	String coffName = readVirtualMemoryString(file, 256);
    	if (coffName == null) return -1;
    	if (argc < 0 || argc > 16) return -1;
    	byte[] buffer = new byte[4];
    	String[] arguments = new String[argc];
    	int offset = 4;
    	for(int i= 0; i < argc; i++){
    		//read the content from virtual address to the buffer according to the offset and index i
    		int tempRead = readVirtualMemory(vaddrArgv+offset*i, buffer);
    		//if read successfully
    		if(tempRead == buffer.length){
    			arguments[i] = readVirtualMemoryString(Lib.bytesToInt(buffer, 0), 256);
    		} else {
    			return -1;
    		}
    		if(arguments[i] == null){
    			return -1;
    		}
    	}
    	
    	UserProcess childProcess = newUserProcess();
    	childProcess.parentProcess = this;
    	int childPID = -1;
    	UserKernel.proLock.acquire();
    	boolean check = childProcess.execute(coffName, arguments);
    	//UserKernel.runningQueue.add(childProcess);
    	if(check){
    		childPID = childProcess.pid;
    		childrenSet.add(childPID);
    	}
    	UserKernel.proLock.release();
    	
    	return childPID;	
    }
    
    private static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2,
    syscallJoin = 3, syscallCreate = 4, syscallOpen = 5,
    syscallRead = 6, syscallWrite = 7, syscallClose = 8,
    syscallUnlink = 9;
    
    /**
     * Handle a syscall exception. Called by <tt>handleException()</tt>. The
     * <i>syscall</i> argument identifies which syscall the user executed:
     *
     * <table>
     * <tr>
     * <td>syscall#</td>
     * <td>syscall prototype</td>
     * </tr>
     * <tr>
     * <td>0</td>
     * <td><tt>void halt();</tt></td>
     * </tr>
     * <tr>
     * <td>1</td>
     * <td><tt>void exit(int status);</tt></td>
     * </tr>
     * <tr>
     * <td>2</td>
     * <td><tt>int  exec(char *name, int argc, char **argv);
     * 								</tt></td>
     * </tr>
     * <tr>
     * <td>3</td>
     * <td><tt>int  join(int pid, int *status);</tt></td>
     * </tr>
     * <tr>
     * <td>4</td>
     * <td><tt>int  creat(char *name);</tt></td>
     * </tr>
     * <tr>
     * <td>5</td>
     * <td><tt>int  open(char *name);</tt></td>
     * </tr>
     * <tr>
     * <td>6</td>
     * <td><tt>int  read(int fd, char *buffer, int size);
     * 								</tt></td>
     * </tr>
     * <tr>
     * <td>7</td>
     * <td><tt>int  write(int fd, char *buffer, int size);
     * 								</tt></td>
     * </tr>
     * <tr>
     * <td>8</td>
     * <td><tt>int  close(int fd);</tt></td>
     * </tr>
     * <tr>
     * <td>9</td>
     * <td><tt>int  unlink(char *name);</tt></td>
     * </tr>
     * </table>
     * 
     * @param syscall the syscall number.
     * @param a0 the first syscall argument.
     * @param a1 the second syscall argument.
     * @param a2 the third syscall argument.
     * @param a3 the fourth syscall argument.
     * @return the value to be returned to the user.
     */
    public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
        switch (syscall) {
            case syscallHalt:
                return handleHalt();
            case syscallExit:
                return handleExit(a0);
            case syscallCreate:
                return handleCreate(a0);
            case syscallOpen:
                return handleOpen(a0);
            case syscallRead:
                return handleRead(a0, a1, a2);
            case syscallWrite:
                return handleWrite(a0, a1,a2);
            case syscallClose:
                return handleClose(a0);
            case syscallUnlink:
                return handleUnlink(a0);
            case syscallExec:
            	return handleExec(a0,a1,a2);
            case syscallJoin:
            	return handleJoin(a0,a1);
            default:
                Lib.debug(dbgProcess, "Unknown syscall " + syscall);
                Lib.assertNotReached("Unknown system call!");
        }
        return 0;
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
            case Processor.exceptionSyscall:
                int result = handleSyscall(processor.readRegister(Processor.regV0),
                                           processor.readRegister(Processor.regA0),
                                           processor.readRegister(Processor.regA1),
                                           processor.readRegister(Processor.regA2),
                                           processor.readRegister(Processor.regA3));
                processor.writeRegister(Processor.regV0, result);
                processor.advancePC();
                break;
                
            default:
                Lib.debug(dbgProcess, "Unexpected exception: "
                          + Processor.exceptionNames[cause]);
                abnormalExitStatus = 1;
                handleExit(0);
                Lib.assertNotReached("Unexpected exception");
        }
    }
    
    /** The program being run by this process. */
    protected Coff coff;
    
    /** This process's page table. */
    protected TranslationEntry[] pageTable;
    
    /** The number of contiguous pages occupied by the program. */
    protected int numPages;
    
    /** The number of pages in the program's stack. */
    protected final int stackPages = 8;
    
    private int initialPC, initialSP;
    
    private int argc, argv;
    
    private static final int pageSize = Processor.pageSize;
    private static int abnormalExitStatus = 0;
    private static final char dbgProcess = 'a';
    private int pid;
    private int runningCounter = 0;
    private Condition childCV = new Condition(UserKernel.proLock);
    private HashSet<Integer> childrenSet = new HashSet<Integer>();
    private HashMap<Integer, Integer> exitStatusMap = new HashMap<Integer, Integer>();
    private UserProcess parentProcess = null;
    protected OpenFile[] fileTable;
    protected static final int MAXFILE = 16;
}
