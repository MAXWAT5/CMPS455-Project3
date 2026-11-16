import java.util.Random;
import java.util.concurrent.Semaphore;

class Dispatcher implements Runnable {

    // tasks not arrived yet (null once moved to queue)
    public Task[] arrivals;
    // the ready queue
    public Task[] queueHere;
    // number of tasks currently in queueHere
    public int queueCount;

    // MUTEX!!!!!!!!!!!!!!! YAY
    public Semaphore queueMutex = new Semaphore(1);

    //so dispatch and core can communicate to each other via changing variables
    public Core theCore;

    //core releases when task finish and dispatch waits on it to wake it up

    //random alogrithm enumerator
    public algorithms algo;
    public int tasksRemain;
    public int roundRobinQuantum;

    Dispatcher(algorithms algo, int roundRobinQuantum, Task[] queueHere, int queueCount, Task[] arrivals, Core theCore, int T) {
        this.algo = algo;
        this.roundRobinQuantum = roundRobinQuantum;
        this.queueHere = queueHere;
        this.queueCount = queueCount;
        this.arrivals = arrivals;
        this.theCore = theCore;
        this.tasksRemain = T;
    }

    public void run() {
        System.out.println("Dispatcher "+theCore.coreNum+"    | Using CPU "+theCore.coreNum);
        System.out.println("Dispatcher "+theCore.coreNum+"    | Now releasing dispatchers.");
        System.out.println();
        if (algo == algorithms.RR) {
            System.out.println("Dispatcher "+theCore.coreNum+"    | Running RR algorithm, Time Quantum = " + roundRobinQuantum);
        }
        else if (algo == algorithms.FCFS) {
            System.out.println("Dispatcher "+theCore.coreNum+"    | Running FCFS algorithm");
        }
        else {
            System.out.println("Dispatcher "+theCore.coreNum+"    | Running " + algo);
        }

        Task last = null;

        try {
            while (tasksRemain>0) {
                // waits until core finishes (first time it goes through cause of main)
                Main.waitSemaphoreForDispatch[theCore.coreNum-1].acquire();

                //mutex block
                queueMutex.acquire();

                if (last != null) {
                    // task decrements remaining but keep a separate one for dispatcher so it doesn't lose track in the ready queue
                    if (last.remaining > 0) {
                        queueHere[queueCount] = last;
                        queueCount++;
                    }
                    else {
                        tasksRemain--;
                    }
                }
                if (tasksRemain == 0) {
                    queueMutex.release();
                    break;
                }

                // new arrived tasks go into the quueeue here
                boolean arrivedNow = false;
                for (int i = 0; i < arrivals.length; i++) {
                    // after every run, core advances the internal clock so you just keep going until
                    if (arrivals[i] != null && arrivals[i].arrivalTime <= theCore.internalClock) {
                        queueHere[queueCount] = arrivals[i];
                        queueCount++;
                        arrivals[i] = null;
                        arrivedNow = true;
                    }
                }
                if (arrivedNow) {
                    System.out.println();
                    System.out.println("--------------- Ready Queue ---------------");
                    for (int i = 0; i < queueCount; i++) {
                        System.out.println("ID:" + queueHere[i].taskID + ", Max Burst:" + queueHere[i].totalBurst + ", Current Burst:" + (queueHere[i].totalBurst - queueHere[i].remaining));
                    }
                    System.out.println("-------------------------------------------");
                    System.out.println();
                }

                // next arrival is the nearest new value that's in the queue
                int nextArrival = Integer.MAX_VALUE;
                for (int i = 0; i < arrivals.length; i++) {
                    if (arrivals[i] != null && arrivals[i].arrivalTime < nextArrival) {
                        nextArrival = arrivals[i].arrivalTime;
                    }
                }

                // cpu twiddling its thumbs while it waits to release the semaphores
                if (queueCount == 0) {
                    theCore.internalClock = nextArrival;
                    last = null;
                    queueMutex.release();
                    Main.waitSemaphoreForDispatch[theCore.coreNum-1].release();
                    continue;
                }

                // select a task
                int pick = 0;
                if (algo == algorithms.NSJF || algo == algorithms.PSJF) {
                    for (int i = 1; i < queueCount; i++) {
                        if (queueHere[i].remaining < queueHere[pick].remaining) {
                            pick = i;
                        }
                    }
                }


                Task task = queueHere[pick];

                //shifts everything in the quueeue left so there's no gap
                for (int i = pick; i < queueCount - 1; i++) {
                    queueHere[i] = queueHere[i + 1];
                }
                queueCount--;
                queueHere[queueCount] = null;
                queueMutex.release();



                // burst just decide how long it runs
                int burst=0;

                if (algo == algorithms.PSJF && nextArrival != Integer.MAX_VALUE && task!=null) {
                    // this runs until done or until the next arrival, which may [pre-empt]
                    burst = Math.min(task.remaining, nextArrival - theCore.internalClock);
                }
                else if (algo == algorithms.RR && task!=null) {
                    burst = Math.min(roundRobinQuantum, task.remaining);
                }
                else if (task!=null){
                    burst = task.remaining;
                }
                else {
                    theCore.finished = true;
                    break;
                }
                System.out.println();
                System.out.println("Dispatcher "+theCore.coreNum+"    | Running process " + task.taskID);

                //reads the next new task and alloted burst time after the semaphore is returned to it
                theCore.theTaskAtHand = task;
                theCore.allotedBurstTime = burst;
                last = task;
                //core is waken up which activates task being woken up laters
                theCore.coreStart.release();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        //checks that core is done and ends the while loop in core
        theCore.finished = true;

        // wakes up core
        theCore.coreStart.release();
    }
}

// the setter of all time basically
class Core implements Runnable {
    public Task theTaskAtHand;
    public int allotedBurstTime;
    public int coreNum;
    public volatile int internalClock = 0;
    public volatile static boolean finished = false;
    public Semaphore coreStart = new Semaphore(0);

    Core(int coreNum) {
        this.coreNum = coreNum;
    }

    public void run() {
        try {
            while (!finished) {
                if (!coreStart.tryAcquire()) {
                    continue;
                }
                Task theTask = theTaskAtHand;
                theTask.allotedCPU = allotedBurstTime;
                System.out.println(theTask.taskID+" "+coreNum);
                theTask.taskStart.release();
                theTask.taskFinished.acquire();
                internalClock = internalClock + allotedBurstTime;
                Main.waitSemaphoreForDispatch[coreNum-1].release();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}

class Task implements Runnable {
    public int taskID;
    public int totalBurst;

    // cycles still needed
    public int remaining;
    public int allotedCPU;
    public int arrivalTime;


    // core releases this semaphore for the task later
    public Semaphore taskStart = new Semaphore(0);


    // released by task for core to acquire after every slice
    public Semaphore taskFinished = new Semaphore(0);

    Task(int taskID, int totalBurst, int arrivalTime) {
        this.taskID = taskID;
        this.totalBurst = totalBurst;
        this.remaining = totalBurst;
        this.arrivalTime = arrivalTime;
    }

    public void run() {
        try {
            taskStart.acquire();
            while (remaining > 0) {


                // this has to wait till core releases this specific tasks start semaphore


                //current burst
                int cb = totalBurst - remaining;

                System.out.println("Proc. Thread " + taskID + (taskID < 10 ? "  " : " ") + "| On CPU: MB=" + totalBurst  + ", CB=" + cb + ", BT=" + allotedCPU + ", BG:=" + (cb + allotedCPU));


                // ONE CPU CYLCE PER ITERATION ---------VVVVVVVVVVVV
                // core sets allotedCPU for the cpu time to run the task for
                for (int i = 1; i <= allotedCPU; i++) {

                    remaining--;
                    System.out.println("Proc. Thread " + taskID + (taskID < 10 ? "  " : " ") + "| Using CPU "+Thread.currentThread()+"; On burst " + (cb + i) + ".");
                }
                // waking up the core

            }
            taskFinished.release();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}

// choosing the algorithm to run yay yippee yay
enum algorithms {
    FCFS("FCFS"),
    RR("Round Robin"),
    NSJF("Non Preemptive - Shortest Job First"),
    PSJF("Preemptive - Shortest Job First");

    public String num;

    algorithms(String num) {
        this.num = num;
    }

    public String toString() {
        return num;
    }
}

public class Main {
    public static Task[] tasks;
    public static Semaphore[] waitSemaphoreForDispatch;
    public static void main(String[] args) throws Exception {
        int coreCount=4;
        algorithms algo = algorithms.values()[0];
        int quantumArg = 2;
        try {
            if (args[0].equals("-S")) {
                algo = algorithms.values()[Integer.parseInt(args[1]) - 1];
            }
            else if(args[0].equals("-C")){
                coreCount = Integer.parseInt(args[1]);
                if(args[2].equals("-S")){

                }
            }
            else {
                algo = algorithms.valueOf(args[0].toUpperCase());
                quantumArg = 1;
            }
        }
        catch (Exception e) {
            System.out.println("Invalid input; quitting program.");
        }
        int roundRobinQuantum = 4;
        if (args.length > quantumArg) {
            roundRobinQuantum = Integer.parseInt(args[quantumArg]);
        }

        Random rand = new Random();
        int T = rand.nextInt(25)+1;      // [1, 25]
        int B = rand.nextInt(50)+1;      // [1, 50]

        if (algo == algorithms.RR) {
            System.out.println("Scheduler Algorithm Select: " + algo + ". Time Quantum = " + roundRobinQuantum);
        } else {
            System.out.println("Scheduler Algorithm Select: " + algo);
        }
        System.out.println("# threads = " + T);

        int[] bursts = new int[T];
        int totalWork = 0;
        for (int i = 0; i < T; i++) {
            bursts[i] = rand.nextInt(B) + 1;
            totalWork += bursts[i];
        }

        // dispatcher acquires this semaphore on its first run and core just keeps releasing it later as a wakeup call


        //drools all over the arrays
        tasks = new Task[T];
        Task[] arrivals = new Task[T];
        Task[] queueHere = new Task[T];
        int queueCount = 0;
        Thread[] threads = new Thread[T];

        for (int i = 0; i < T; i++) {
            int arrival = 0;
            if (algo == algorithms.PSJF) {
                arrival = rand.nextInt(Math.max(1, totalWork / 2)) + 1;
            }
            tasks[i] = new Task(i, bursts[i], arrival);

            // each task is blocked cause of core (say thank you core)
            threads[i] = new Thread(tasks[i]);
            threads[i].start();


            System.out.println("Main thread     | Creating process thread " + i);
            if (arrival == 0) {
                queueHere[queueCount] = tasks[i];
                queueCount++;
            } else {
                arrivals[i] = tasks[i];
            }
        }

        System.out.println();

        System.out.println("--------------- Ready Queue ---------------");
        for (int i = 0; i < queueCount; i++) {
            System.out.println("ID:" + queueHere[i].taskID + ", Max Burst:" + queueHere[i].totalBurst + ", Current Burst:" + (queueHere[i].totalBurst - queueHere[i].remaining));
        }
        System.out.println("-------------------------------------------");

        System.out.println();

        waitSemaphoreForDispatch=new Semaphore[coreCount];
        Core[] theCores = new Core[coreCount];
        Thread[] dispatcherThread = new Thread[coreCount];
        Thread[] coreThread = new Thread[coreCount];
        for(int i=0; i<coreCount; i++)
        {
            waitSemaphoreForDispatch[i] = new Semaphore(1);
            theCores[i] = new Core(i+1);
            coreThread[i] = new Thread(theCores[i]);
            coreThread[i].start();
            Dispatcher dispatcher = new Dispatcher(algo, roundRobinQuantum, queueHere, queueCount, arrivals, theCores[i], T);
            dispatcherThread[i] = new Thread(dispatcher);
            dispatcherThread[i].start();
        }

        // core and dispatch are sharing the waitsempahorefordispatch cause core has to wake up dispatch





        System.out.println("Main thread     | Forking dispatchers");

        for (int i = 0; i < T; i++) {
            threads[i].join();
        }

        for(int i=0; i<coreCount; i++)
        {
            coreThread[i].join();
            dispatcherThread[i].join();
        }
        System.out.println();
        System.out.println("Main thread     | Exiting.");
    }
}