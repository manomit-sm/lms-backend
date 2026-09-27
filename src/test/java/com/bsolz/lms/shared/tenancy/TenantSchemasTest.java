package com.bsolz.lms.shared.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class TenantSchemasTest {

	@ParameterizedTest
	@CsvSource({ "acme, tenant_acme", "acme-corp, tenant_acme_corp", "a1b, tenant_a1b" })
	void derivesSchemaFromTenantKey(String key, String schema) {
		assertThat(TenantSchemas.schemaFor(key)).isEqualTo(schema);
		assertThat(TenantSchemas.requireValidSchema(schema)).isEqualTo(schema);
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = { "", "ab", "Acme", "1acme", "acme-", "acme_corp", "acme corp", "acme\"; drop schema public;--",
			"a-very-long-tenant-key-that-exceeds-forty-chars" })
	void rejectsInvalidTenantKeys(String key) {
		assertThatIllegalArgumentException().isThrownBy(() -> TenantSchemas.schemaFor(key));
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = { "", "pg_catalog", "information_schema", "tenant_", "tenant_Acme", "tenant_acme\"",
			"other_acme", "public;drop" })
	void rejectsSchemaNamesOutsideThePattern(String schema) {
		assertThatIllegalArgumentException().isThrownBy(() -> TenantSchemas.requireValidSchema(schema));
	}

}
