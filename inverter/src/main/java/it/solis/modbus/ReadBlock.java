package it.solis.modbus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/** Insieme di registri letti con un'unica richiesta Modbus. */
public record ReadBlock(int start, int quantity, List<SolisRegister> registers) {

    /** Molti inverter Solis rifiutano letture più lunghe di ~50 registri. */
    public static final int MAX_REGISTERS_PER_REQUEST = 50;

    /** Raggruppa i registri in blocchi contigui di al massimo {@value #MAX_REGISTERS_PER_REQUEST} word. */
    public static List<ReadBlock> plan(Collection<SolisRegister> registers) {
        return plan(registers, Integer.MAX_VALUE);
    }

    /**
     * Come {@link #plan(Collection)}, ma apre un nuovo blocco anche quando tra due registri ci sono più di
     * {@code maxGap} indirizzi non definiti (per non leggere indirizzi che l'inverter potrebbe rifiutare).
     */
    public static List<ReadBlock> plan(Collection<SolisRegister> registers, int maxGap) {
        var sorted = registers.stream().sorted(Comparator.comparingInt(SolisRegister::address)).toList();
        List<ReadBlock> blocks = new ArrayList<>();
        List<SolisRegister> current = new ArrayList<>();
        int start = 0;
        int end = 0;
        for (SolisRegister reg : sorted) {
            if (!current.isEmpty() && (reg.lastAddress() - start + 1 > MAX_REGISTERS_PER_REQUEST
                    || reg.address() - end - 1 > maxGap)) {
                blocks.add(new ReadBlock(start, end - start + 1, List.copyOf(current)));
                current.clear();
            }
            if (current.isEmpty()) {
                start = reg.address();
            }
            current.add(reg);
            end = Math.max(end, reg.lastAddress());
        }
        if (!current.isEmpty()) {
            blocks.add(new ReadBlock(start, end - start + 1, List.copyOf(current)));
        }
        return blocks;
    }
}
