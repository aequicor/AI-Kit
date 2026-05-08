package com.aikit.setup.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ArgsTest {

    @Test
    fun schemaWithoutFlagsDefaultsToJson() {
        val cmd = Args.parse(arrayOf("schema"))
        assertEquals(Command.Schema(SchemaFormat.JSON), cmd)
    }

    @Test
    fun schemaAcceptsFormatHumanSpaceSeparated() {
        val cmd = Args.parse(arrayOf("schema", "--format", "human"))
        assertEquals(Command.Schema(SchemaFormat.HUMAN), cmd)
    }

    @Test
    fun schemaAcceptsFormatEqualsSyntax() {
        val cmd = Args.parse(arrayOf("schema", "--format=json"))
        assertEquals(Command.Schema(SchemaFormat.JSON), cmd)
    }

    @Test
    fun schemaRejectsUnknownFormat() {
        val cmd = Args.parse(arrayOf("schema", "--format", "yaml"))
        val err = assertIs<Command.Error>(cmd)
        assertTrue("yaml" in err.message)
    }

    @Test
    fun schemaRejectsPositionalArgs() {
        val cmd = Args.parse(arrayOf("schema", "extra"))
        assertIs<Command.Error>(cmd)
    }

    @Test
    fun schemaRejectsMissingFormatValue() {
        val cmd = Args.parse(arrayOf("schema", "--format"))
        assertIs<Command.Error>(cmd)
    }
}
