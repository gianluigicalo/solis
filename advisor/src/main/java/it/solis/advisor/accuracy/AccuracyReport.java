package it.solis.advisor.accuracy;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Schema(description = "Confronto tra la produzione FV reale e quella stimata da Open-Meteo (energie in Wh)")
public record AccuracyReport(
        @Schema(description = "Inizio del periodo (inizio della prima ora)") Instant from,
        @Schema(description = "Fine del periodo (fine dell'ultima ora completa)") Instant to,
        @Schema(description = "Previsione archiviata confrontata con il reale") ForecastSelection forecast,
        @Schema(description = "Performance ratio usato per la stima") double performanceRatio,
        Summary summary,
        @Schema(description = "Totali per giorno (fuso dell'advisor), solo sulle ore confrontabili") List<Day> days,
        @Schema(description = "Totali per stringa, solo sulle ore confrontabili") Map<String, StringTotals> strings,
        @Schema(description = "Ogni ora del periodo, anche quelle escluse dal confronto") List<Hour> hours,
        @Schema(description = "Osservazioni su come leggere il risultato") List<String> notes) {

    public enum Verdict { AFFIDABILE, DISCRETA, INAFFIDABILE, DATI_INSUFFICIENTI }

    @Schema(description = "Energie in Wh. Errori della stima: positivo = stima più alta del reale")
    public record Summary(
            @Schema(description = "Ore di luce confrontate tra reale e previsione") int hoursCompared,
            @Schema(description = "Ore escluse perché la batteria era carica (produzione forse limitata)") int hoursLimited,
            @Schema(description = "Ore escluse per letture dell'inverter incomplete") int hoursIncomplete,
            @Schema(description = "Ore senza una previsione abbastanza anticipata") int hoursWithoutForecast,
            double actualWh,
            double forecastWh,
            @Schema(description = "Stima con l'irraggiamento a posteriori, sulle stesse ore") Double modelWh,
            @Schema(description = "Errore sul totale della previsione, in %") Double forecastErrorPct,
            @Schema(description = "Errore orario medio della previsione (somma |stima - reale| / somma reale), in %")
            Double forecastHourlyErrorPct,
            @Schema(description = "Errore sul totale della stima a posteriori: misura il modello dell'impianto, "
                    + "non la previsione") Double modelErrorPct,
            Double modelHourlyErrorPct,
            @Schema(description = "Performance ratio che avrebbe azzerato l'errore della stima a posteriori")
            Double suggestedPerformanceRatio,
            Verdict verdict,
            String verdictText) {
    }

    public record Day(LocalDate day, int hours, double actualWh, double forecastWh, Double modelWh,
                      Double forecastErrorPct) {
    }

    public record StringTotals(@Schema(description = "Potenza di picco (W)") double peakW, Double actualWh, double forecastWh, Double modelWh,
                               Double forecastErrorPct) {
    }

    public record Hour(
            @Schema(description = "Fine dell'ora") Instant time,
            @Schema(description = "Produzione reale (Wh), null se non ci sono letture") Double actualWh,
            @Schema(description = "Stima dalla previsione (Wh)") Double forecastWh,
            @Schema(description = "Stima dall'irraggiamento a posteriori (Wh)") Double modelWh,
            @Schema(description = "Quando è stata scaricata la previsione usata") Instant forecastIssuedAt,
            @Schema(description = "Quota dell'ora coperta da letture dell'inverter (0..1)") double coverage,
            @Schema(description = "SOC medio della batteria nell'ora") Double batterySoc,
            @Schema(description = "Usata nel confronto, oppure il motivo dell'esclusione")
            String status,
            @Schema(description = "Produzione reale per stringa (Wh)") Map<String, Double> actualByString,
            @Schema(description = "Stima dalla previsione per stringa (Wh)") Map<String, Double> forecastByString) {
    }
}
