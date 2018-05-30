import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

//class Receiver implements ReceiverInterface {
//    public Receiver(RestaurantManagement rm) {
//        this.rm = rm;
//    }
//
//    @Override
//    public void mealReady(int orderID) {
//        System.out.println("Rc::mealReady(" + orderID + ")");
//        rm.on_mealReady(orderID);
//    }
//
//    private RestaurantManagement rm;
//}
//
//class Orderer implements OrderInterface {
//    public Orderer(RestaurantManagement rm) {
//        this.rm = rm;
//    }
//
//    @Override
//    public void newOrder(int orderID, int tableID) {
//        System.out.println("Or::newOrder(" + orderID + ", " + tableID + ")");
//        rm.on_newOrder(orderID, tableID);
//    }
//
//    @Override
//    public void orderComplete(int orderID, int tableID) {
//        System.out.println("Or::orderComplete(" + orderID + ", " + tableID + ")");
//        rm.on_orderComplete(orderID, tableID);
//    }
//
//    private RestaurantManagement rm;
//}
//
//class Kitchen implements ReceiverInterface, {
//    public Kitchen(KitchenInterface kitchen) {
//        kitchen.registerReceiver(this);
//        this.kitchen = kitchen;
//    }
//
//    void addOrderToPrepare(int orderID) {
//        cookingOrders.add(orderID);
//    }
//
//    @Override
//    public void mealReady(int orderID) {
//        System.out.println("Kt::mealReady(" + orderID + ")");
//
//
//        rm.on_mealReady(orderID);
//    }
//
//    public boolean prepare(int orderID) {
//        if (tasks.get() < kitchen.getNumberOfParallelTasks()) {
//            kitchen.prepare(orderID);
//            return true;
//        }
//
//        return false;
//    }
//
//    KitchenInterface kitchen;
//
//    private PriorityBlockingQueue<Integer> cookingOrders = new PriorityBlockingQueue<>();
//    private AtomicInteger tasks = new AtomicInteger(0);
//}

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

class RestaurantManagement implements RestaurantManagementInterface, OrderInterface, ReceiverInterface {
    @Override
    public void addWaiter(WaiterInterface waiter) {
//        System.out.println("RM::addWaiter(" + waiter.getID() + ")");
        waiters.add(new Waiter(waiter, this));
    }

    @Override
    public void removeWaiter(WaiterInterface waiter) {
        System.out.println("RM::removeWaiter(" + waiter.getID() + ")");

        if (waiters.get(waiter.getID()).getStaus() == Waiter.Status.READY) {
            removeWaiterHelper(waiter);
            System.out.println("RM::removeWaiter(" + waiter.getID() + ") - waiter status Free => removed");
        } else {
            waiters.get(waiter.getID()).setStatus(Waiter.Status.TO_REMOVE);
            System.out.println("RM::removeWaiter(" + waiter.getID() + ") - waiter is working => remove later when meal complete");
        }
    }

    public void removeWaiterHelper(WaiterInterface waiter) {
        if (waiters.get(waiter.getID()).getStaus() == Waiter.Status.WORKING)
            System.out.println("ERROR: invalid waiter remove *******************************************************");

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

    private KitchenInterface kitchen;
    private AtomicInteger kitchenTasks = new AtomicInteger(0);

    private Vector<Waiter> waiters = new Vector<>();

    private PriorityBlockingQueue<Integer> newOrdersQueue = new PriorityBlockingQueue<>();
    private ConcurrentHashMap<Integer, Integer> order2table = new ConcurrentHashMap<>();

    private PriorityBlockingQueue<Integer> mealReadyQueue = new PriorityBlockingQueue<>();

    private void newOrderHelper() {
        while (!newOrdersQueue.isEmpty()) {
            if (kitchenTasks.get() < Math.min(kitchen.getNumberOfParallelTasks(), waiters.size())) {
                kitchenTasks.incrementAndGet();
                int id = newOrdersQueue.poll();
                kitchen.prepare(id);
//                System.out.println("RM::newOrder() - kitchen start cooking: " + id + ". Tasks: " + kitchenTasks.get());
            } else {
//                System.out.println("RM::newOrder() - kitchen cannot start cook new order(" + newOrdersQueue.size() + ") current tasks: " + kitchenTasks.get());
                return;
            }
        }
    }

    @Override
    public void newOrder(int orderID, int tableID) {
        synchronized (this) {
        System.out.println("RM::newOrder(" + orderID + ", " + tableID + ")");
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