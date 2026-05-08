package com.aikit.setup.manifest.profile

import com.aikit.setup.manifest.BlockYamlParser
import com.aikit.setup.manifest.Manifest
import com.aikit.setup.manifest.RawNode
import com.aikit.setup.manifest.asStringList
import com.aikit.setup.manifest.field
import com.aikit.setup.manifest.path
import com.aikit.setup.manifest.stringOrNull
import com.aikit.setup.templates.InMemoryTemplateRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class ProfileResolverTest {

    private val parser = BlockYamlParser()

    private fun manifest(yaml: String): Manifest = Manifest(parser.parse(yaml.trimIndent()))

    private fun resolverWith(files: Map<String, String>): ProfileResolver {
        val templates = InMemoryTemplateRegistry(files)
        return ProfileResolver(ProfileLoader(templates, parser))
    }

    private val sampleLanguageProfile = """
        _profile_name: typescript-pnpm
        _profile_description: "TS + pnpm baseline"
        _profile_axis: language

        stack:
          languages: [typescript]
          build_command: "pnpm build"

        policies:
          forbidden_patterns:
            - "no console.log"
            - "no any"
    """.trimIndent()

    private val sampleCapabilityProfile = """
        _profile_name: solid
        _profile_description: "SOLID rules"
        _profile_axis: capability

        policies:
          forbidden_patterns:
            - "single responsibility"
    """.trimIndent()

    private val sampleFrameworkProfile = """
        _profile_name: nextjs
        _profile_description: "Next.js"
        _profile_axis: framework

        ui:
          framework: "Next.js (React)"
          platforms: ["Web"]

        stack:
          frameworks: [next.js]

        policies:
          forbidden_patterns:
            - "no use client without hooks"
    """.trimIndent()

    @Test
    fun emptyProfilesListReturnsManifestUnchanged() {
        val resolver = resolverWith(emptyMap())
        val source = manifest(
            """
            project: { name: x, slug: x }
            stack:
              profiles: []
              languages: [kotlin]
            """,
        )
        val result = resolver.resolve(source)
        assertTrue(result is ProfileResolver.Result.Success)
        // stack.profiles got stripped; everything else preserved.
        assertNull(result.manifest.raw.path("stack", "profiles"))
        assertEquals(listOf("kotlin"), result.manifest.raw.path("stack", "languages").asStringList())
    }

    @Test
    fun missingStackProfilesAltogetherIsAlsoOk() {
        val resolver = resolverWith(emptyMap())
        val source = manifest(
            """
            project: { name: x, slug: x }
            stack:
              languages: [kotlin]
            """,
        )
        val result = resolver.resolve(source)
        assertTrue(result is ProfileResolver.Result.Success)
        assertEquals(emptyList(), result.resolved)
    }

    @Test
    fun unknownProfileReportsNotFound() {
        val resolver = resolverWith(emptyMap())
        val source = manifest(
            """
            stack:
              profiles: [does-not-exist]
            """,
        )
        val result = resolver.resolve(source)
        assertTrue(result is ProfileResolver.Result.Failure)
        assertEquals(listOf("profile_not_found"), result.errors.map { it.code })
    }

    @Test
    fun axisMismatchIsRejected() {
        val resolver = resolverWith(
            mapOf(
                // file lives under language/ but declares capability
                "profiles/language/imposter.yaml" to """
                    _profile_name: imposter
                    _profile_description: "wrong axis"
                    _profile_axis: capability
                """.trimIndent(),
            ),
        )
        val source = manifest(
            """
            stack:
              profiles: [imposter]
            """,
        )
        val result = resolver.resolve(source)
        assertTrue(result is ProfileResolver.Result.Failure)
        assertEquals(listOf("profile_axis_mismatch"), result.errors.map { it.code })
    }

    @Test
    fun fieldOutsideAxisIsRejected() {
        val resolver = resolverWith(
            mapOf(
                "profiles/capability/sneaky.yaml" to """
                    _profile_name: sneaky
                    _profile_description: "trespasses on framework axis"
                    _profile_axis: capability

                    ui:
                      framework: "React"

                    stack:
                      languages: [typescript]
                """.trimIndent(),
            ),
        )
        val source = manifest(
            """
            stack:
              profiles: [sneaky]
            """,
        )
        val result = resolver.resolve(source)
        assertTrue(result is ProfileResolver.Result.Failure)
        // Two trespasses: top-level `ui` and top-level `stack`.
        val codes = result.errors.map { it.code }.toSet()
        assertEquals(setOf("profile_field_outside_axis"), codes)
        assertEquals(2, result.errors.size)
    }

    @Test
    fun nestedSubKeyOutsideAxisIsRejected() {
        // capability axis owns policies, but only specific sub-keys.
        val resolver = resolverWith(
            mapOf(
                "profiles/capability/oops.yaml" to """
                    _profile_name: oops
                    _profile_description: "writes a non-allowed sub-key"
                    _profile_axis: capability

                    policies:
                      forbidden_patterns: ["ok"]
                      model_constraints:        # not in the capability whitelist
                        planning: { min_tier: reasoner }
                """.trimIndent(),
            ),
        )
        val source = manifest(
            """
            stack:
              profiles: [oops]
            """,
        )
        val result = resolver.resolve(source)
        assertTrue(result is ProfileResolver.Result.Failure)
        assertEquals(1, result.errors.size)
        val err = result.errors.single()
        assertEquals("profile_field_outside_axis", err.code)
        assertTrue(err.message.contains("model_constraints"), "got: ${err.message}")
    }

    @Test
    fun twoLanguageProfilesViolateCardinality() {
        val resolver = resolverWith(
            mapOf(
                "profiles/language/typescript-pnpm.yaml" to sampleLanguageProfile,
                "profiles/language/kotlin-gradle.yaml" to """
                    _profile_name: kotlin-gradle
                    _profile_description: "Kotlin"
                    _profile_axis: language

                    stack:
                      languages: [kotlin]
                """.trimIndent(),
            ),
        )
        val source = manifest(
            """
            stack:
              profiles: [typescript-pnpm, kotlin-gradle]
            """,
        )
        val result = resolver.resolve(source)
        assertTrue(result is ProfileResolver.Result.Failure)
        assertEquals(listOf("profile_cardinality_violation"), result.errors.map { it.code })
    }

    @Test
    fun duplicateProfileNameIsRejected() {
        val resolver = resolverWith(
            mapOf("profiles/capability/solid.yaml" to sampleCapabilityProfile),
        )
        val source = manifest(
            """
            stack:
              profiles: [solid, solid]
            """,
        )
        val result = resolver.resolve(source)
        assertTrue(result is ProfileResolver.Result.Failure)
        assertEquals(listOf("profile_duplicate"), result.errors.map { it.code })
    }

    @Test
    fun successfulMergeAccumulatesForbiddenPatternsInCanonicalOrder() {
        val resolver = resolverWith(
            mapOf(
                "profiles/language/typescript-pnpm.yaml" to sampleLanguageProfile,
                "profiles/framework/nextjs.yaml" to sampleFrameworkProfile,
                "profiles/capability/solid.yaml" to sampleCapabilityProfile,
                "profiles/capability/security-baseline.yaml" to """
                    _profile_name: security-baseline
                    _profile_description: "security defaults"
                    _profile_axis: capability

                    policies:
                      forbidden_patterns:
                        - "no hardcoded secrets"
                """.trimIndent(),
            ),
        )
        val source = manifest(
            """
            project: { name: x, slug: x }
            stack:
              profiles: [solid, nextjs, typescript-pnpm, security-baseline]
              build_command: "pnpm run build"
            """,
        )
        val result = resolver.resolve(source)
        assertTrue(result is ProfileResolver.Result.Success)

        // All four profiles' forbidden_patterns concatenated, deduped, in
        // canonical merge order: security-baseline → language → framework → other capabilities.
        val patterns = result.manifest.raw.path("policies", "forbidden_patterns").asStringList()
        assertEquals(
            listOf(
                "no hardcoded secrets",       // security-baseline first
                "no console.log", "no any",   // language
                "no use client without hooks", // framework
                "single responsibility",       // remaining capability
            ),
            patterns,
        )

        // Base manifest's explicit build_command overrides profile default.
        assertEquals("pnpm run build", result.manifest.raw.path("stack", "build_command").stringOrNull())

        // ui block came from framework profile.
        assertEquals("Next.js (React)", result.manifest.raw.path("ui", "framework").stringOrNull())
    }

    @Test
    fun baseManifestOverridesProfileScalars() {
        val resolver = resolverWith(
            mapOf("profiles/language/typescript-pnpm.yaml" to sampleLanguageProfile),
        )
        val source = manifest(
            """
            stack:
              profiles: [typescript-pnpm]
              build_command: "pnpm overridden"
              compile_command: "tsc -p ."
            """,
        )
        val result = resolver.resolve(source)
        assertTrue(result is ProfileResolver.Result.Success)
        assertEquals(
            "pnpm overridden",
            result.manifest.raw.path("stack", "build_command").stringOrNull(),
        )
        // compile_command came from base (profile didn't set it).
        assertEquals(
            "tsc -p .",
            result.manifest.raw.path("stack", "compile_command").stringOrNull(),
        )
    }

    @Test
    fun resolvedManifestHasNoStackProfilesLeftover() {
        val resolver = resolverWith(
            mapOf("profiles/capability/solid.yaml" to sampleCapabilityProfile),
        )
        val source = manifest(
            """
            stack:
              profiles: [solid]
              languages: [kotlin]
            """,
        )
        val result = resolver.resolve(source)
        assertTrue(result is ProfileResolver.Result.Success)
        // stack.profiles must be stripped so downstream layers don't trip on it.
        assertNull(result.manifest.raw.path("stack", "profiles"))
    }

    @Test
    fun toolsListMergesByIdAcrossProfileAndBase() {
        val resolver = resolverWith(
            mapOf(
                "profiles/language/typescript-pnpm.yaml" to """
                    _profile_name: typescript-pnpm
                    _profile_description: "TS"
                    _profile_axis: language

                    tools:
                      - id: lsp-typescript
                        kind: lsp
                        command: typescript-language-server
                """.trimIndent(),
            ),
        )
        val source = manifest(
            """
            stack:
              profiles: [typescript-pnpm]
            tools:
              - id: lsp-typescript
                command: tsserver-custom
              - id: web-search
                kind: builtin
            """,
        )
        val result = resolver.resolve(source)
        assertTrue(result is ProfileResolver.Result.Success)
        val tools = result.manifest.raw.path("tools")
        if (tools !is RawNode.Sequence) fail("expected sequence; got $tools")
        val byId = tools.items.associate {
            (it as RawNode.Mapping).entries["id"]!!.stringOrNull()!! to it
        }
        assertEquals(setOf("lsp-typescript", "web-search"), byId.keys)
        // base overrides profile's command, kind kept from profile.
        assertEquals("tsserver-custom", byId["lsp-typescript"]?.field("command")?.stringOrNull())
        assertEquals("lsp", byId["lsp-typescript"]?.field("kind")?.stringOrNull())
    }
}
