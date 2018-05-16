import java.util.Vector;

class RMReceiver implements ReceiverInterface {
    public RMReceiver() {
        System.out.println("RMR::RMReceiver()");

        orders = new Vector<>();
    }

    @Override
    public void mealReady(int orderID) {
        System.out.println("RMR::mealReady(" + orderID + ")");
    }

    Vector<Integer> orders;
}

public class RestaurantManagement implements RestaurantManagementInterface {
    public RestaurantManagement() {
        System.out.println("RM::RestaurantManagement()");

        waiters = new Vector<>();
    }

    @Override
    public void addWaiter(WaiterInterface waiter) {
        System.out.println("RM::addWaiter(" + waiter.getID() + ")");

        waiters.add(waiter);
    }

    @Override
    public void removeWaiter(WaiterInterface waiter) {
        System.out.println("RM::removeWaiter(" + waiter.getID() + ")");

        waiters.remove(waiter);
    }

    @Override
    public void setKitchen(KitchenInterface kitchen) {
        System.out.println("RM::setKitchen(" + kitchen.getNumberOfParallelTasks() + ")");

        this.kitchen = kitchen;


    }

    Vector<WaiterInterface> waiters;
    KitchenInterface kitchen;
}
