package it.solis.modbus;

import com.digitalpetri.modbus.codec.Modbus;
import com.digitalpetri.modbus.master.ModbusTcpMaster;
import com.digitalpetri.modbus.master.ModbusTcpMasterConfig;
import com.digitalpetri.modbus.requests.ReadHoldingRegistersRequest;
import com.digitalpetri.modbus.requests.ReadInputRegistersRequest;
import com.digitalpetri.modbus.responses.ReadHoldingRegistersResponse;
import com.digitalpetri.modbus.responses.ReadInputRegistersResponse;
import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCountUtil;
import it.solis.config.SolisProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Client Modbus TCP verso l'inverter. La connessione viene aperta alla prima richiesta
 * e ristabilita automaticamente da {@link ModbusTcpMaster} se cade.
 */
@Component
public class SolisModbusClient implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(SolisModbusClient.class);

    private final ModbusTcpMaster master;
    private final SolisProperties properties;

    public SolisModbusClient(SolisProperties properties) {
        this.properties = properties;
        this.master = new ModbusTcpMaster(new ModbusTcpMasterConfig.Builder(properties.host())
                .setPort(properties.port())
                .setTimeout(properties.timeout())
                .build());
    }

    /** Indirizzi tollerati senza definizione dentro un blocco di holding register (vedi {@link ReadBlock#plan}). */
    private static final int HOLDING_MAX_GAP = 2;

    /** Blocchi di holding register già segnalati come non leggibili: il warning si scrive una volta sola. */
    private final Set<Integer> failedHoldingBlocks = ConcurrentHashMap.newKeySet();

    /**
     * Legge i registri indicati: input register con la funzione 0x04, holding register (impostazioni modificabili,
     * 43xxx) con la funzione 0x03. Solo lettura: questo client non scrive mai sull'inverter. Le richieste sono
     * serializzate: il datalogger Solis non gestisce bene richieste concorrenti.
     *
     * @throws InverterCommunicationException se fallisce la lettura di un blocco di input register; un blocco di
     *                                        holding register non leggibile lascia invece i suoi valori a null
     */
    public synchronized Map<SolisRegister, Object> read(Collection<SolisRegister> registers) {
        connect();
        Map<SolisRegister, Object> values = new EnumMap<>(SolisRegister.class);
        List<SolisRegister> input = registers.stream().filter(r -> !r.holding()).toList();
        List<SolisRegister> holding = registers.stream().filter(SolisRegister::holding).toList();
        for (ReadBlock block : ReadBlock.plan(input)) {
            readBlock(block, false, values);
        }
        for (ReadBlock block : ReadBlock.plan(holding, HOLDING_MAX_GAP)) {
            try {
                readBlock(block, true, values);
                failedHoldingBlocks.remove(block.start());
            } catch (InverterCommunicationException e) {
                if (Thread.currentThread().isInterrupted()) {
                    throw e;
                }
                if (failedHoldingBlocks.add(block.start())) {
                    log.warn("Holding register {}-{} non leggibili, valori lasciati vuoti: {}",
                            block.start(), block.start() + block.quantity() - 1, e.getMessage());
                }
                block.registers().forEach(r -> values.put(r, null));
            }
        }
        return values;
    }

    private void readBlock(ReadBlock block, boolean holding, Map<SolisRegister, Object> values) {
        Object response = null;
        try {
            ByteBuf buf;
            if (holding) {
                var r = master.<ReadHoldingRegistersResponse>sendRequest(
                                new ReadHoldingRegistersRequest(block.start(), block.quantity()), properties.unitId())
                        .get(properties.timeout().toMillis() * 2, TimeUnit.MILLISECONDS);
                response = r;
                buf = r.getRegisters();
            } else {
                var r = master.<ReadInputRegistersResponse>sendRequest(
                                new ReadInputRegistersRequest(block.start(), block.quantity()), properties.unitId())
                        .get(properties.timeout().toMillis() * 2, TimeUnit.MILLISECONDS);
                response = r;
                buf = r.getRegisters();
            }
            for (SolisRegister reg : block.registers()) {
                values.put(reg, RegisterDecoder.decode(buf, (reg.address() - block.start()) * 2, reg));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InverterCommunicationException("Lettura interrotta", e);
        } catch (ExecutionException | TimeoutException e) {
            Throwable cause = e instanceof ExecutionException ? e.getCause() : e;
            throw new InverterCommunicationException("Lettura %s %d-%d da %s:%d fallita: %s".formatted(
                    holding ? "holding register" : "registri", block.start(), block.start() + block.quantity() - 1,
                    properties.host(), properties.port(), cause), cause);
        } finally {
            if (response != null) {
                ReferenceCountUtil.release(response);
            }
        }
    }

    /** Legge {@code count} input register a partire da {@code start} e restituisce i valori grezzi a 16 bit. */
    public synchronized int[] readRaw(int start, int count) {
        connect();
        ReadInputRegistersResponse response = null;
        try {
            response = master.<ReadInputRegistersResponse>sendRequest(
                            new ReadInputRegistersRequest(start, count), properties.unitId())
                    .get(properties.timeout().toMillis() * 2, TimeUnit.MILLISECONDS);
            var buf = response.getRegisters();
            int[] values = new int[count];
            for (int i = 0; i < count; i++) {
                values[i] = buf.getUnsignedShort(i * 2);
            }
            return values;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InverterCommunicationException("Lettura interrotta", e);
        } catch (ExecutionException | TimeoutException e) {
            Throwable cause = e instanceof ExecutionException ? e.getCause() : e;
            throw new InverterCommunicationException("Lettura registri %d-%d fallita: %s".formatted(
                    start, start + count - 1, cause), cause);
        } finally {
            if (response != null) {
                ReferenceCountUtil.release(response);
            }
        }
    }

    /** Apre la connessione se non è già attiva (se lo è, {@code connect()} restituisce quella esistente). */
    private void connect() {
        try {
            master.connect().get(properties.timeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InverterCommunicationException("Connessione interrotta", e);
        } catch (ExecutionException | TimeoutException e) {
            Throwable cause = e instanceof ExecutionException ? e.getCause() : e;
            throw new InverterCommunicationException("Connessione a %s:%d fallita: %s".formatted(
                    properties.host(), properties.port(), cause), cause);
        }
    }

    @Override
    public void destroy() {
        try {
            master.disconnect().get(properties.timeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.debug("Errore in disconnessione dall'inverter", e);
        }
        Modbus.releaseSharedResources();
    }
}
