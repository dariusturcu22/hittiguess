package org.dariusturcu.backend.ratelimit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClientAddressKeyTest {

    @Test
    void ipv4AddressesAreKeyedAsTheyAre() {
        assertThat(ClientAddressKey.of("198.51.100.7")).isEqualTo("198.51.100.7");
    }

    @Test
    void ipv6AddressesInTheSameSlash64ShareAKey() {
        assertThat(ClientAddressKey.of("2001:db8:1:2::1"))
                .isEqualTo(ClientAddressKey.of("2001:db8:1:2:aaaa:bbbb:cccc:dddd"));
    }

    @Test
    void ipv6AddressesInDifferentSlash64sGetDifferentKeys() {
        assertThat(ClientAddressKey.of("2001:db8:1:2::1")).isNotEqualTo(ClientAddressKey.of("2001:db8:1:3::1"));
    }

    @Test
    void ipv4MappedIpv6AddressesAreKeyedAsIpv4() {
        assertThat(ClientAddressKey.of("::ffff:198.51.100.7")).isEqualTo("198.51.100.7");
    }

    @Test
    void unparseableAddressesAreKeyedAsTheyAre() {
        assertThat(ClientAddressKey.of("not:an:address:zz")).isEqualTo("not:an:address:zz");
    }
}
