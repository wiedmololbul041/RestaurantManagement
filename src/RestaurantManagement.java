import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

class Waiter {
    public static AtomicInteger total = new AtomicInteger(0);
    public static AtomicInteger free = new AtomicInteger(0);

    enum Status {
        READY,
        WORKING,
        TO_REMOVE
    }

    public Waiter(WaiterInterface waiter, OrderInterface orderInterface) {
        this.waiter = waiter;
        this.waiter.registerOrder(orderInterface);
        this.status = Status.READY;
        total.incrementAndGet();
        free.incrementAndGet();
    }

    public int getID() { return waiter.getID(); }

    public void go(int orderID, int tableID) {
        this.orderID = orderID;
        this.tableID = tableID;
        status = Status.WORKING;
        waiter.go(orderID, tableID);
    }

    public Status getStaus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public int getOrderID() { return orderID; }
    public int getTableID() { return tableID; }
    public WaiterInterface getWaiter() { return waiter; }

    private WaiterInterface waiter;
    private Status status;
    private int orderID;
    private int tableID;
}

class StartCooker extends Thread {
    public StartCooker(Semaphore startCookingSem, RestaurantManagement rm) {
        this.rm = rm;
        this.startCookingSem = startCookingSem;
    }

    public void run() {
        while (true) {
//            System.out.println("StartCooker .... ");
            rm.newOrderHelper();

            try {
                this.join(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    RestaurantManagement rm;
    private Semaphore startCookingSem;
}

class RestaurantManagement implements RestaurantManagementInterface, OrderInterface, ReceiverInterface {
    public RestaurantManagement() {
        sc = new StartCooker(startCookingSem, this);
        sc.start();
    }

    @Override
    public void addWaiter(WaiterInterface waiter) {
//        System.out.println("RM::addWaiter(" + waiter.getID() + ")");
        waiters.add(new Waiter(waiter, this));
    }

    @Override
    public void removeWaiter(WaiterInterface waiter) {
//        System.out.println("RM::removeWaiter(" + waiter.getID() + ")");

        if (waiters.get(waiter.getID()).getStaus() == Waiter.Status.READY) {
            removeWaiterHelper(waiter);
//            System.out.println("RM::removeWaiter(" + waiter.getID() + ") - waiter status Free => removed");
        } else {
            waiters.get(waiter.getID()).setStatus(Waiter.Status.TO_REMOVE);
//            System.out.println("RM::removeWaiter(" + waiter.getID() + ") - waiter is working => remove later when meal complete");
        }
    }

    public void removeWaiterHelper(WaiterInterface waiter) {
//        if (waiters.get(waiter.getID()).getStaus() == Waiter.Status.WORKING)
//            System.out.println("ERROR: invalid waiter remove *******************************************************");

        Waiter.total.decrementAndGet();
        Waiter.free.decrementAndGet();
        waiters.remove(waiter.getID());
    }

    @Override
    public void setKitchen(KitchenInterface kitchen) {
//        System.out.println("RM::setKitchen(" + kitchen + ")");
        kitchen.registerReceiver(this);
        this.kitchen = kitchen;
    }

    private Semaphore startCookingSem = new Semaphore(1);
    private StartCooker sc;

    private KitchenInterface kitchen;
    private AtomicInteger kitchenTasks = new AtomicInteger(0);

    private Vector<Waiter> waiters = new Vector<>();

    private PriorityBlockingQueue<Integer> newOrdersQueue = new PriorityBlockingQueue<>();
    private ConcurrentHashMap<Integer, Integer> order2table = new ConcurrentHashMap<>();

    private PriorityBlockingQueue<Integer> mealReadyQueue = new PriorityBlockingQueue<>();

    public void newOrderHelper() {
        try {
            startCookingSem.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        while (!newOrdersQueue.isEmpty()) {
//            System.out.println(kitchenTasks.get() + " < Math.min(" + kitchen.getNumberOfParallelTasks() + ", " + waiters.size() + ")");
            if (kitchenTasks.get() < Math.min(kitchen.getNumberOfParallelTasks(), waiters.size())) {
                kitchenTasks.incrementAndGet();
                int id = newOrdersQueue.poll();
                kitchen.prepare(id);
                startCookingSem.release();
//                System.out.println("RM::newOrder() - kitchen start cooking: " + id + ". Tasks: " + kitchenTasks.get());
            } else {
//                System.out.println("RM::newOrder() - kitchen cannot start cook new order(" + newOrdersQueue.size() + ") current tasks: " + kitchenTasks.get());
                startCookingSem.release();
                return;
            }
        }

        startCookingSem.release();
    }

    @Override
    public void newOrder(int orderID, int tableID) {
        synchronized (this) {

            order2table.put(orderID, tableID);
            newOrdersQueue.add(orderID);

            newOrderHelper();
        }
    }

    private void mealReadyHelper() {
        for (Waiter w : waiters) {
            if (mealReadyQueue.isEmpty())
                return;

            if (w.getStaus() == Waiter.Status.READY) {
                int goOrderID = mealReadyQueue.poll();
                w.go(goOrderID, order2table.get(goOrderID));
            }
        }
    }

    @Override
    public void mealReady(int orderID) {
        synchronized (this) {
//        System.out.println("RM::mealReady(" + orderID + ")");
            kitchenTasks.decrementAndGet(); // one order done => +1 free task

            mealReadyQueue.add(orderID);

            mealReadyHelper();
            newOrderHelper();
        }
    }

    @Override
    public void orderComplete(int orderID, int tableID) {
        synchronized (this) {
//        System.out.println("RM::orderComplete(" + orderID + ", " + tableID + ")");
            for (int i = 0; i < waiters.size(); ++i) {
                Waiter w = waiters.get(i);

//                System.out.println("--- " + w.getOrderID() + " == " + orderID);
                if (w.getOrderID() == orderID) {


                    if (w.getStaus() == Waiter.Status.WORKING) {
//                        System.out.println("RM::orderComplete(" + orderID + ", " + tableID + ") - kelner " + w.getID() + " zaniusl order " + orderID + " do " + tableID + " i jest gotowy do dalszej pracy");
                        w.setStatus(Waiter.Status.READY);

                        newOrderHelper();
                    } else if (w.getStaus() == Waiter.Status.TO_REMOVE) {
//                        System.out.println("RM::orderComplete(" + orderID + ", " + tableID + ") - kelner " + w.getID() + "obsluzyl dzis ostatniego klienta ... bay");
                        removeWaiterHelper(w.getWaiter());
                    }


                }
            }

            newOrderHelper();
            mealReadyHelper();
        }
    }
}