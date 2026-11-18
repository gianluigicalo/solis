package it.solis.modbus;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReadBlockTest {

    @Test
    void blocksRespectMaxSizeAndCoverEveryRegister() {
        var all = EnumSet.allOf(SolisRegister.class);
        List<ReadBlock> blocks = ReadBlock.plan(all);

        assertThat(blocks).allSatisfy(b -> {
            assertThat(b.quantity()).isBetween(1, ReadBlock.MAX_REGISTERS_PER_REQUEST);
            assertThat(b.registers()).allSatisfy(r -> {
                assertThat(r.address()).isGreaterThanOrEqualTo(b.start());
                assertThat(r.lastAddress()).isLessThan(b.start() + b.quantity());
            });
        });
        assertThat(blocks.stream().mapToInt(b -> b.registers().size()).sum()).isEqualTo(all.size());
    }

    @Test
    void registerAddressesDoNotOverlap() {
        var sorted = EnumSet.allOf(SolisRegister.class).stream()
                .sorted((a, b) -> Integer.compare(a.address(), b.address())).toList();
        for (int i = 1; i < sorted.size(); i++) {
            assertThat(sorted.get(i).address())
                    .as("%s si sovrappone a %s", sorted.get(i), sorted.get(i - 1))
                    .isGreaterThan(sorted.get(i - 1).lastAddress());
        }
    }
}
