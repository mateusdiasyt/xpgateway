import { Router } from "express";
import { stationAuth } from "../../middleware/stationAuth";
import { HttpError } from "../../core/httpError";
import { getLastPaymentByStation, getStationWithPricing } from "./stations.service";

export const stationsRouter = Router();

stationsRouter.get("/:stationId/config", stationAuth, async (req, res, next) => {
  try {
    const stationId = req.params.stationId;

    if (!req.station || req.station.id !== stationId) {
      throw new HttpError(403, "Acesso negado para esta estação.");
    }

    const data = await getStationWithPricing(stationId);
    return res.json(data);
  } catch (error) {
    return next(error);
  }
});

stationsRouter.get("/:stationId/last-payment", stationAuth, async (req, res, next) => {
  try {
    const stationId = req.params.stationId;

    if (!req.station || req.station.id !== stationId) {
      throw new HttpError(403, "Acesso negado para esta estação.");
    }

    const data = await getLastPaymentByStation(stationId);
    return res.json({ data });
  } catch (error) {
    return next(error);
  }
});
