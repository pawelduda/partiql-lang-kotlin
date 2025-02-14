/*
 * Copyright 2019 Amazon.com, Inc. or its affiliates.  All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 *  You may not use this file except in compliance with the License.
 * A copy of the License is located at:
 *
 *      http://aws.amazon.com/apache2.0/
 *
 *  or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific
 *  language governing permissions and limitations under the License.
 */

package org.partiql.cli

import com.amazon.ion.*
import org.partiql.lang.*
import org.partiql.lang.eval.*
import org.partiql.cli.OutputFormat.*
import java.io.*    

/**
 * TODO builder, kdoc
 */
internal class Cli(private val valueFactory: ExprValueFactory,
                   private val input: InputStream,
                   private val output: OutputStream,
                   private val format: OutputFormat,
                   private val compilerPipeline: CompilerPipeline,
                   private val globals: Bindings,
                   private val query: String) : PartiQLCommand {
    override fun run() {
        val inputIonValue = valueFactory.ion.iterate(input).asSequence().map { valueFactory.newFromIonValue(it) }
        val inputExprValue = valueFactory.newBag(inputIonValue)

        val bindings = Bindings.buildLazyBindings { 
            addBinding("input_data") { inputExprValue }
        }.delegate(globals)

        val result = compilerPipeline.compile(query).eval(EvaluationSession.build { globals(bindings) })
        
        when(format) {
            ION_TEXT   -> valueFactory.ion.newTextWriter(output).use { printIon(it, result) }
            ION_BINARY -> valueFactory.ion.newBinaryWriter(output).use { printIon(it, result) }
            PARTIQL    -> NonConfigurableExprValuePrettyPrinter(output).prettyPrint(result)
        }
    }
    
    private fun printIon(ionWriter: IonWriter, value: ExprValue) {
        when(value.type) {
            // writes top level bags as a datagram
            ExprValueType.BAG -> value.iterator().forEach { v -> v.ionValue.writeTo(ionWriter) }
            else              -> value.ionValue.writeTo(ionWriter) 
        }
    }
}
