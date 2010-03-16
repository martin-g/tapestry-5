// Copyright 2008, 2010 The Apache Software Foundation
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.apache.tapestry5.internal.services;

import org.apache.tapestry5.NullFieldStrategy;
import org.apache.tapestry5.ioc.util.AvailableValues;
import org.apache.tapestry5.ioc.util.UnknownValueException;
import org.apache.tapestry5.services.NullFieldStrategySource;

import java.util.Map;

public class NullFieldStrategySourceImpl implements NullFieldStrategySource
{
    private final Map<String, NullFieldStrategy> configuration;

    public NullFieldStrategySourceImpl(Map<String, NullFieldStrategy> configuration)
    {
        this.configuration = configuration;
    }

    public NullFieldStrategy get(String name)
    {
        NullFieldStrategy result = configuration.get(name);

        if (result != null) return result;
        
        throw new UnknownValueException(String.format("Unrecognized name '%s' locating a null field strategy.", name),
                new AvailableValues("null field strategies", configuration));

    }
}
