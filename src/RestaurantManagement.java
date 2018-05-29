import java.util.ArrayList;
import java.util.Queue;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

enum WaiterStatus {
    READY,
    WORKING,
    TO_REMOVE
}

class Receiver implements ReceiverInterface {
    public Receiver(RestaurantManagement rm) {
        this.rm = rm;
    }

    @Override
    public void mealReady(int orderID) {
        System.out.println("Rc::mealReady(" + orderID + ")");
        rm.on_mealReady(orderID);
    }

    private RestaurantManagement rm;
}

class Orderer implements OrderInterface {
    public Orderer(RestaurantManagement rm) {
        this.rm = rm;
    }

    @Override
    public void newOrder(int orderID, int tableID) {
        System.out.println("Or::newOrder(" + orderID + ", " + tableID + ")");
        rm.on_newOrder(orderID, tableID);
    }

    @Override
    public void orderComplete(int orderID, int tableID) {
        System.out.println("Or::orderComplete(" + orderID + ", " + tableID + ")");
        rm.on_orderComplete(orderID, tableID);
    }

    private RestaurantManagement rm;
}

public class RestaurantManagement implements RestaurantManagementInterface {
    @Override
    public void addWaiter(WaiterInterface waiter) {
        System.out.println("RM::addWaiter(" + waiter.getID() + ")");

        waiters.put(waiter.getID(), waiter);
        waiterStatus.put(waiter.getID(), WaiterStatus.READY);
        freeWaiters.incrementAndGet();
        waiter.registerOrder(orderer);
    }

    @Override
    public void removeWaiter(WaiterInterface waiter) {
        System.out.println("RM::removeWaiter(" + waiter.getID() + ")");
        waiterStatus.put(waiter.getID(), WaiterStatus.TO_REMOVE);
        toRemoveWaiters.add(waiter.getID());
    }

    @Override
    public void setKitchen(KitchenInterface kitchen) {
        System.out.println("RM::setKitchen(" + kitchen + ")");
        kitchen.registerReceiver(receiver);
    }

    void on_newOrder(int orderID, int tableID) {
        System.out.println("RM::on_newOrder(" + orderID + ", " + tableID + ")");
        newOrdersQueue.add(orderID);
        order2table.put(orderID, tableID);
    }

    void on_mealReady(int orderID) {
        System.out.println("RM::on_mealReady(" + orderID + ")");
        mealReadyQueue.add(orderID);
    }

    void on_orderComplete(int orderID, int tableID) {
        System.out.println("RM::on_orderComplete(" + orderID + ", " + tableID + ")");
        mealReadyQueue.add(orderID);


    }

    void try_cook_orders() {
        removeWaiters();

        int currentTasks = kitchenTasks.get() + (waiters.size() - freeWaiters.get());
        if (currentTasks > kitchen.getNumberOfParallelTasks())
            return;

        if (newOrdersQueue.size() <= 0)
            return;

        kitchen.prepare(newOrdersQueue.poll());
        kitchenTasks.incrementAndGet();
    }

    void try_send_waiter_with_order() {
        if (mealReadyQueue.isEmpty())
            return;

        if
    }

    void removeWaiters() {
//        while (!toRemoveWaiters.isEmpty()) {
//            int id = toRemoveWaiters.poll();
//            if (waiterStatus.get(id) == WaiterStatus.READY)
//
//            waiters.remove(id)
//        }
    }



    private Receiver receiver = new Receiver(this);
    private Orderer orderer = new Orderer(this);
    private KitchenInterface kitchen;
    private AtomicInteger kitchenTasks = new AtomicInteger(0);

    private ConcurrentHashMap<Integer, WaiterInterface> waiters = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Integer, WaiterStatus> waiterStatus = new ConcurrentHashMap<>();
    private PriorityBlockingQueue<Integer> toRemoveWaiters = new PriorityBlockingQueue<>();
    private AtomicInteger freeWaiters = new AtomicInteger(0);

    private PriorityBlockingQueue<Integer> newOrdersQueue = new PriorityBlockingQueue<>();
    private ConcurrentHashMap<Integer, Integer> order2table = new ConcurrentHashMap<>();

    private PriorityBlockingQueue<Integer> mealReadyQueue = new PriorityBlockingQueue<>();
    private ConcurrentHashMap<Integer, Integer> waiter2order = new ConcurrentHashMap<>();

}