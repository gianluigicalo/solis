package it.solis.modbus;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RegisterDecoderTest {

    @Test
    void decodesScaledUnsigned16() {
        var buf = Unpooled.buffer().writeShort(3987);
        assertThat(RegisterDecoder.decode(buf, 0, SolisRegister.PV1_VOLTAGE)).isEqualTo(new BigDecimal("398.7"));
    }

    @Test
    void decodesSigned32HighWordFirst() {
        var buf = Unpooled.buffer().writeShort(0xFFFF).writeShort(0xF830); // -2000
        assertThat(RegisterDecoder.decode(buf, 0, SolisRegister.METER_ACTIVE_POWER)).isEqualTo(new BigDecimal("-2000"));
    }

    @Test
    void appliesMultiplierForNegativeDecimals() {
        var buf = Unpooled.buffer().writeShort(60);
        assertThat(RegisterDecoder.decode(buf, 0, SolisRegister.EPM_EXPORT_LIMIT)).isEqualTo(new BigDecimal("6000"));
    }

    @Test
    void returnsNullWhenRegisterReportsUnavailable() {
        var buf = Unpooled.buffer().writeShort(0xFFFF);
        assertThat(RegisterDecoder.decode(buf, 0, SolisRegister.SELFTEST_59S1_VOLTAGE)).isNull();
    }

    @Test
    void decodesHexAndAscii() {
        assertThat(RegisterDecoder.decode(Unpooled.buffer().writeShort(0x3173), 0, SolisRegister.PRODUCT_MODEL))
                .isEqualTo("0x3173");

        var sn = Unpooled.buffer().writeBytes("103173024A290497".getBytes()).writeZero(16);
        assertThat(RegisterDecoder.decode(sn, 0, SolisRegister.SERIAL_NUMBER)).isEqualTo("103173024A290497");
    }
}
