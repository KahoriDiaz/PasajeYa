import { getFirestore, Timestamp } from "firebase-admin/firestore";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { FUNCTIONS_REGION } from "../config/region.js";

/**
 * Tarea programada diaria para marcar beneficios vencidos como 'expired'.
 */
export const expireBenefits = onSchedule(
  {
    schedule: "0 0 * * *", // Cada medianoche
    region: FUNCTIONS_REGION,
    timeZone: "America/Lima"
  },
  async () => {
    const db = getFirestore();
    const now = Timestamp.now();

    const expiredUsers = await db
      .collection("users")
      .where("benefit.status", "==", "approved")
      .where("benefit.validUntil", "<", now)
      .get();

    if (expiredUsers.empty) return;

    const batch = db.batch();
    for (const doc of expiredUsers.docs) {
      batch.update(doc.ref, {
        "benefit.status": "expired",
        "benefit.updatedAt": now
      });
    }

    await batch.commit();
    console.log(`Se marcaron ${expiredUsers.size} beneficios como vencidos.`);
  }
);
