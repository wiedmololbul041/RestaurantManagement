import java.lang.reflect.Array;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

class KitchenManager {
    class Receiver implements ReceiverInterface {
        public Receiver(Queue<Integer> cookedOrders) {
            System.out.println("KM::R::Receiver()");

            mealsReady = cookedOrders;
        }

        @Override
        public void mealReady(int orderID) {
            System.out.println("RM::R::mealReady(" + orderID + ")");

            onMealReady(orderID);
        }

        /**
         * Kolejka zamówień, które są zrealizowane prze kuchnię i czekają do dostarczenia przez kelnera (tj. jeszcze nie trafiły do stolików)
         */
        Queue<Integer> mealsReady;
    }

    public KitchenManager(KitchenInterface kitchenInterface, RestaurantManagement rm) {
        receiver = new Receiver(cookedOrders);

        kitchen = kitchenInterface;
        kitchen.registerReceiver(receiver);

        this.rm = rm;
    }

    public int getNumberOfParallelTasks() {
        return kitchen.getNumberOfParallelTasks();
    }

    public int getNumberOfWorkingTasks() {
        return cookingOrders.size();
    }

    public void prepare(int orderID) {
        System.out.println("KM::prepare(" + orderID + ")");

        long startTime = System.nanoTime();
        cookingOrders.add(orderID);
        kitchen.prepare(orderID);
        long estimatedTime = System.nanoTime() - startTime;
        System.out.println("KM::prepare(" + orderID + ") - time execution = " + estimatedTime);

    }

    public void onMealReady(int orderID) {
        System.out.println("KM::onMealReady(" + orderID + ")");

        cookedOrders.add(orderID);
        cookingOrders.remove(orderID);

        rm.onMealReady(cookedOrders);
    }

    public boolean hasMealReady() {
        return cookedOrders.size() > 0;
    }

    public int popMealReady() {
        return cookedOrders.poll();
    }

    private KitchenInterface kitchen;
    private Receiver receiver;
    private RestaurantManagement rm;

    /**
     * Kolejka zamówień które przetwarza kuchnia
     */
    private final Queue<Integer> cookingOrders = new ConcurrentLinkedDeque<>();

    /**
     * Kolejka zamówień które są do odebrania z kuchni
     */
    private final Queue<Integer> cookedOrders = new ConcurrentLinkedDeque<>();
}


class MealOrderManager implements OrderInterface {
    class MealOrder {
        public MealOrder(int orderID, int tableID) {
            this.orderID = orderID;
            this.tableID = tableID;
        }

        public int orderID;
        public int tableID;
    }

    public MealOrderManager(RestaurantManagement rm) {
        System.out.println("MOM::MealOrderManager()");

        this.rm = rm;
    }

    @Override
    public void newOrder(int orderID, int tableID) {
        System.out.println("MOM::newOrder(" + orderID + ", " + tableID + ")");

        rm.onNewOrder(orderID, tableID);
    }

    @Override
    public void orderComplete(int orderID, int tableID) {
        System.out.println("MOM::orderComplete(" + orderID + ", " + tableID + ")");

        // TODO
//        order2table.remove(orderID);
    }

    private RestaurantManagement rm;

}

public class RestaurantManagement implements RestaurantManagementInterface {
    public enum WaiterState {
        WAITING_FOR_KITHEN,
        WORKING;
    }

    public class WaiterManager {
        public class Waiter {
            public Waiter(WaiterInterface waiter, OrderInterface orderInterface) {
                this.waiter = waiter;
                state = WaiterState.WAITING_FOR_KITHEN;

                waiter.registerOrder(orderInterface);
            }

            public void go(int orderID, int tableID) {
                System.out.println("MW::W(" + waiter.getID() + ")::go(" + orderID + ", " + tableID + ")");
                state = WaiterState.WORKING;
                waiter.go(orderID, tableID);
            }

            private WaiterState state;
            private WaiterInterface waiter;
        }

        public WaiterManager(MealOrderManager mealOrderManager) {
            this.mealOrderManager = mealOrderManager;

            waiters = new Vector<>();
        }

        public void addWaiter(WaiterInterface waiter) {
            System.out.println("WM::addWaiter(" + waiter.getID() + ")" + waiters.size() + 1);

            Waiter newWaiter = new Waiter(waiter, mealOrderManager);
            waiters.add(newWaiter);

            waitingWaiters.incrementAndGet();
            // TODO - dodać jeszcze decrement do tego jak juz waiter dostarczy posiłek
        }

        public void removeWaiter(WaiterInterface waiter) {
            System.out.println("WM::removeWaiter(" + waiter.getID() + ")" + (waiters.size() - 1));

            waiters.remove(waiter);
        }

        void onMealReady(Queue<Integer> mealsReady) {
            ArrayList<Waiter> freeWaiters = new ArrayList<>();
            for (Waiter w : waiters)
                if (w.state == WaiterState.WAITING_FOR_KITHEN)
                    freeWaiters.add(w);

            while (mealsReady.size() > 0) {
                if (freeWaiters.size() > 0) {
                    int orderID = mealsReady.poll();
                    int tableID = getTableID(orderID);
                    freeWaiters.get(0).go(orderID, tableID);
                    freeWaiters.remove(0);
                }
            }
        }

        public int getNumberOfWaiters() {
            return waiters.size();
        }

        public int freeWaiters() {
            return waitingWaiters.get();
        }

        public int workingWaiters() {
            return getNumberOfWaiters() - waitingWaiters.get();
        }

        public boolean go(int orderID, int tableID) {
            if (waitingWaiters.get() <= 0) {
                System.out.println("ERROR: WM::go(" + orderID + ", " + tableID + ") - no waiting waiters!");
                return false;
            }

            Waiter freeWaiter = null;
            for (Waiter waiter : waiters)
                if (waiter.state == WaiterState.WAITING_FOR_KITHEN)
                    freeWaiter = waiter;

            if (freeWaiter == null) {
                System.out.println("ERROR: WM::go(" + orderID + ", " + tableID + ") - no free waiters!");
                return false;
            }

            waitingWaiters.incrementAndGet();
            freeWaiter.go(orderID, tableID);

            return true;
        }

        Vector<Waiter> waiters;
        private MealOrderManager mealOrderManager;

        /**
         * Liczba wolnych kelnerów.
         */
        private final AtomicInteger waitingWaiters = new AtomicInteger(0);
    }

    /*
     * Restaurant Management
     */
    public RestaurantManagement() {
        System.out.println("RM::RestaurantManagement()");

        mealOrderManager = new MealOrderManager(this);
        waiterManager = new WaiterManager(mealOrderManager);
    }

    public void onNewOrder(int orderID, int tableID) {
        System.out.println("RM::onNewOrder(" + orderID + ", " + tableID + ")");

        newOrders.add(orderID);
        order2table.put(orderID, tableID);

        while (newOrderHelper()) {
            ;
        }
    }

    public void onMealReady(Queue<Integer> mealsReady) {
        waiterManager.onMealReady(mealsReady);
    }

    public boolean newOrderHelper() {

        System.out.println("RM::newOrderHelper(): " + kitchenMenager.getNumberOfWorkingTasks() + " < " + kitchenMenager.getNumberOfParallelTasks() + "\n"
                + waiterManager.getNumberOfWaiters() + " > " + kitchenMenager.getNumberOfWorkingTasks());
        if (kitchenMenager.getNumberOfWorkingTasks() < kitchenMenager.getNumberOfParallelTasks() &&
                waiterManager.getNumberOfWaiters() > kitchenMenager.getNumberOfWorkingTasks()) {
            int orderID = newOrders.poll();
            System.out.println("RM::newOrderHelper - kitchen prepare(" + orderID + ")");
            kitchenMenager.prepare(orderID);

            return true;
        }

        return false;
    }

    @Override
    public void addWaiter(WaiterInterface waiter) {
        System.out.println("RM::addWaiter(" + waiter.getID() + ")" + waiterManager.freeWaiters() + 1 + "/" + waiterManager.getNumberOfWaiters() + 1);

        waiterManager.addWaiter(waiter);
    }

    @Override
    public void removeWaiter(WaiterInterface waiter) {
        System.out.println("RM::removeWaiter(" + waiter.getID() + ")" + (waiterManager.freeWaiters() - 1) + "/" + (waiterManager.getNumberOfWaiters() - 1));

        waiterManager.removeWaiter(waiter);
    }

    @Override
    public void setKitchen(KitchenInterface kitchen) {
        System.out.println("RM::setKitchen(" + kitchen.getNumberOfParallelTasks() + ")");

        kitchenMenager = new KitchenManager(kitchen, this);
    }

    public void processOrders() {
        System.out.println("RM::dispatchOrders()");

        System.out.println("RM::processOrders() - " + waiterManager.workingWaiters() + " < " + kitchenMenager.getNumberOfParallelTasks());


//        if (kitchenMenager.hasMealReady()) {
//           if (waiterManager.freeWaiters() > 0) {
//               int orderId = kitchenMenager.popMealReady();
//               int tableID = getTableID(orderId);
//               waiterManager.go(orderId, tableID);
//           }
//        }
    }

    WaiterManager waiterManager;
    KitchenManager kitchenMenager;
    MealOrderManager mealOrderManager;



    /**
     * Liczba zamówień, które maja trafić do kelnerów.
     */
    private final AtomicInteger ordersBeforeInDeliveryState = new AtomicInteger(0);

    /**
     * Mapa przechowująca informacje o powiązaniu zlecenia z stolikiem
     */
    private final static Map<Integer, Integer> order2table = new ConcurrentHashMap<>();

    public int getTableID(int orderID) {
        return order2table.get(orderID);
    }

    /**
     * Kolejka zamówień które maja zostać zlecone kuchni kuchnia
     */
    private final Queue<Integer> newOrders = new ConcurrentLinkedDeque<>();
}
