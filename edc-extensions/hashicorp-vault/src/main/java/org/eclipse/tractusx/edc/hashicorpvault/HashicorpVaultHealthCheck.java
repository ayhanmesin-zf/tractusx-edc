/*
 * Copyright (c) 2022 Mercedes-Benz Tech Innovation GmbH
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contributors:
 *       Mercedes-Benz Tech Innovation GmbH - Add vault health check
 *
 */

package org.eclipse.tractusx.edc.hashicorpvault;

import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.health.HealthCheckResult;
import org.eclipse.edc.spi.system.health.LivenessProvider;
import org.eclipse.edc.spi.system.health.ReadinessProvider;
import org.eclipse.edc.spi.system.health.StartupStatusProvider;

import static java.lang.String.format;

public class HashicorpVaultHealthCheck implements ReadinessProvider, LivenessProvider, StartupStatusProvider {

    private static final String HEALTH_CHECK_ERROR_TEMPLATE =
            "HashiCorp Vault HealthCheck unsuccessful. %s %s";

    private final HashicorpVaultClient client;
    private final Monitor monitor;

    public HashicorpVaultHealthCheck(HashicorpVaultClient client, Monitor monitor) {
        this.client = client;
        this.monitor = monitor;
    }

    @Override
    public HealthCheckResult get() {

        HashicorpVaultHealthResponse response;
        try {
            response = client.getHealth();
        } catch (EdcException e) {  // can be thrown by the client, e.g. on JSON parsing error, etc.
            var exceptionMsg = format(HEALTH_CHECK_ERROR_TEMPLATE, "EdcException: " + e.getMessage(), "");
            monitor.severe(exceptionMsg, e);
            return HealthCheckResult.failed(exceptionMsg);
        }

        switch (response.getCodeAsEnum()) {
            case INITIALIZED_UNSEALED_AND_ACTIVE:
                monitor.debug("HashiCorp Vault HealthCheck successful. " + response.getPayload());
                return HealthCheckResult.success();
            case UNSEALED_AND_STANDBY:
                final String standbyMsg =
                        format(
                                HEALTH_CHECK_ERROR_TEMPLATE, "Vault is in standby", response.getPayload());
                monitor.warning(standbyMsg);
                return HealthCheckResult.failed(standbyMsg);
            case DISASTER_RECOVERY_MODE_REPLICATION_SECONDARY_AND_ACTIVE:
                final String recoveryModeMsg =
                        format(
                                HEALTH_CHECK_ERROR_TEMPLATE, "Vault is in recovery mode", response.getPayload());
                monitor.warning(recoveryModeMsg);
                return HealthCheckResult.failed(recoveryModeMsg);
            case PERFORMANCE_STANDBY:
                final String performanceStandbyMsg =
                        format(
                                HEALTH_CHECK_ERROR_TEMPLATE,
                                "Vault is in performance standby",
                                response.getPayload());
                monitor.warning(performanceStandbyMsg);
                return HealthCheckResult.failed(performanceStandbyMsg);
            case NOT_INITIALIZED:
                final String notInitializedMsg =
                        format(
                                HEALTH_CHECK_ERROR_TEMPLATE, "Vault is not initialized", response.getPayload());
                monitor.warning(notInitializedMsg);
                return HealthCheckResult.failed(notInitializedMsg);
            case SEALED:
                final String sealedMsg =
                        format(HEALTH_CHECK_ERROR_TEMPLATE, "Vault is sealed", response.getPayload());
                monitor.warning(sealedMsg);
                return HealthCheckResult.failed(sealedMsg);
            case UNSPECIFIED:
            default:
                final String unspecifiedMsg =
                        format(
                                HEALTH_CHECK_ERROR_TEMPLATE,
                                "Unspecified response from vault. Code: " + response.getCode(),
                                response.getPayload());
                monitor.warning(unspecifiedMsg);
                return HealthCheckResult.failed(unspecifiedMsg);
        }
    }
}
