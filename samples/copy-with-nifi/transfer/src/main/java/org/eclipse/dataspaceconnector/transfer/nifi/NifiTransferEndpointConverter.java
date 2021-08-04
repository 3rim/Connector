/*
 *  Copyright (c) 2020, 2021 Microsoft Corporation
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Microsoft Corporation - initial API and implementation
 *
 */

package org.eclipse.dataspaceconnector.transfer.nifi;

import org.eclipse.dataspaceconnector.common.string.StringUtils;
import org.eclipse.dataspaceconnector.schema.Schema;
import org.eclipse.dataspaceconnector.schema.SchemaRegistry;
import org.eclipse.dataspaceconnector.schema.SchemaValidationException;
import org.eclipse.dataspaceconnector.spi.security.Vault;
import org.eclipse.dataspaceconnector.spi.types.TypeManager;
import org.eclipse.dataspaceconnector.spi.types.domain.transfer.DataAddress;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class NifiTransferEndpointConverter {
    private final SchemaRegistry schemaRegistry;
    private final Vault vault;
    private final TypeManager typeManager;

    public NifiTransferEndpointConverter(SchemaRegistry registry, Vault vault, TypeManager typeManager) {

        schemaRegistry = registry;
        this.vault = vault;
        this.typeManager = typeManager;
    }

    NifiTransferEndpoint convert(DataAddress dataAddress) {
        var type = dataAddress.getType();

        if (type == null) {
            throw new NifiTransferException("No type was specified!");
        }

        var schema = schemaRegistry.getSchema(type);
        if (schema == null) {
            throw new NifiTransferException("No schema is registered for type " + type);
        }

        validate(dataAddress, schema);


        var keyName = dataAddress.getProperties().get("keyName");

        //need to duplicate the properties here, otherwise the secret would potentially be stored together with the TransferProcess
        Map<String, String> properties = new HashMap<>(dataAddress.getProperties());

        String secret = vault.resolveSecret(keyName);

        //different endpoints might have different credentials, such as SAS token, access key id + secret, etc.
        // this requireds that the credentials are stored as JSON-encoded Map

        Map<String, ?> secretTokenAsMap = typeManager.readValue(secret, Map.class);
        properties.putAll(secretTokenAsMap.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> StringUtils.toString(e.getValue()))));

        return NifiTransferEndpoint.NifiTransferEndpointBuilder.newInstance()
                .type(type)
                .properties(properties)
                .build();
    }

    private void validate(DataAddress dataAddress, Schema schema) {
        Objects.requireNonNull(dataAddress.getKeyName(), "DataAddress must have a keyName!");
        Objects.requireNonNull(dataAddress.getType(), "DataAddress must have a type!");

        //validate required attributes
        schema.getRequiredAttributes().forEach(requiredAttr -> {
            String name = requiredAttr.getName();

            if (dataAddress.getProperty(name) == null) {
                throw new SchemaValidationException("Required property is missing in DataAddress: " + name + " (schema: " + schema.getName() + ")");
            }
        });

        //validate the types of all properties
        schema.getAttributes().forEach(attr -> {
            var type = attr.getType();
        });

    }
}
