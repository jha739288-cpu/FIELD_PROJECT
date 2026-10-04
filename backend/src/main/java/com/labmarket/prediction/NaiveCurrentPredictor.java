package com.labmarket.prediction;

import com.labmarket.entity.EquipmentStatus;
import com.labmarket.entity.MaintenanceStatus;
import com.labmarket.entity.PredictedStatus;
import com.labmarket.prediction.AvailabilityPredictor.Factor;
import com.labmarket.prediction.AvailabilityPredictor.PredictionContext;
import com.labmarket.prediction.AvailabilityPredictor.SlotPrediction;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Naive baseline ("manual/current availability"): a slot is UNAVAILABLE only when
 * a blocking future booking covers it or the item is currently unbookable;
 * otherwise AVAILABLE with neutral confidence. No history is used.
 */
@Component("naive")
public class NaiveCurrentPredictor implements AvailabilityPredictor {

  @Override
  public String method() {
    return "naive";
  }

  @Override
  public List<SlotPrediction> predict(PredictionContext ctx) {
    List<SlotPrediction> out = new ArrayList<>();
    for (var slot : ctx.slots()) {
      boolean booked =
          ctx.futureBookings().stream()
              .anyMatch(b -> b.getStartTime().isBefore(slot.end()) && b.getEndTime().isAfter(slot.start()));
      boolean bookable = isBookable(ctx);
      if (booked) {
        out.add(
            new SlotPrediction(
                slot.start(), slot.end(), PredictedStatus.UNAVAILABLE, 0.0, 1.0,
                List.of(new Factor("existing_booking", 1.0, "A confirmed booking covers this slot"))));
      } else if (!bookable) {
        out.add(
            new SlotPrediction(
                slot.start(), slot.end(), PredictedStatus.UNAVAILABLE, 0.0, 1.0,
                List.of(
                    new Factor(
                        "equipment_state", 0.0,
                        "Item is " + ctx.equipment().getCurrentStatus()
                            + " / " + ctx.equipment().getMaintenanceStatus()))));
      } else {
        out.add(
            new SlotPrediction(
                slot.start(), slot.end(), PredictedStatus.AVAILABLE, 1.0, 0.5,
                List.of(
                    new Factor(
                        "current_state", 1.0,
                        "No booking covers this slot and the item is bookable (no history consulted)"))));
      }
    }
    return out;
  }

  private static boolean isBookable(PredictionContext ctx) {
    var item = ctx.equipment();
    return item.getMaintenanceStatus() == MaintenanceStatus.OPERATIONAL
        && item.getCurrentStatus() != EquipmentStatus.MAINTENANCE
        && item.getCurrentStatus() != EquipmentStatus.SENSOR_OFFLINE;
  }
}
