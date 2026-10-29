import { Timestamp, getFirestore } from "firebase-admin/firestore";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { GPS_CONFIG } from "../config/gps.js";
import { FUNCTIONS_REGION } from "../config/region.js";

export interface CloseStaleTripsResult {
  closed: number;
}

export async function closeStaleTripsHandler(
  now: Date = new Date()
): Promise<CloseStaleTripsResult> {
  const cutoff = Timestamp.fromMillis(
    now.getTime() - GPS_CONFIG.staleTripMs
  );
  const snapshot = await getFirestore()
    .collection("trips")
    .where("status", "==", "open")
    .where("startedAt", "<", cutoff)
    .limit(500)
    .get();

  if (snapshot.empty) return { closed: 0 };

  const batch = getFirestore().batch();
  const endedAt = Timestamp.fromDate(now);
  for (const trip of snapshot.docs) {
    batch.update(trip.ref, {
      status: "requires_review",
      reviewReason: "no_tap_out",
      endedAt
    });
  }
  await batch.commit();

  return { closed: snapshot.size };
}

export const closeStaleTrips = onSchedule(
  {
    region: FUNCTIONS_REGION,
    schedule: "every 15 minutes",
    timeZone: "America/Lima"
  },
  async () => {
    await closeStaleTripsHandler();
  }
);
