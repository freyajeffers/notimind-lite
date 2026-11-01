package com.jeffers.notimindlite.tier1feature

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jeffers.notimindlite.domain.clustering.ClusterNode
import com.jeffers.notimindlite.domain.clustering.DynamicClusterManager
import com.jeffers.notimindlite.domain.clustering.addMatchingSubcategoryKeywords
import com.jeffers.notimindlite.domain.clustering.appDomain
import com.jeffers.notimindlite.domain.clustering.appKeywords
import com.jeffers.notimindlite.domain.clustering.applicationLabel
import com.jeffers.notimindlite.domain.clustering.inferDomainFromPackageName
import com.jeffers.notimindlite.domain.clustering.meaningfulPackageParts
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DynamicClusterManagerDeviceTest {
    @Before
    fun setUp() {
        DynamicClusterManager.resetForTesting()
    }

    @After
    fun tearDown() {
        DynamicClusterManager.resetForTesting()
    }

    @Test
    fun vocabularyInitializationExposesHierarchyAndMatchesDomainSubcategoryAndCaseInsensitiveTerms() {
        DynamicClusterManager.initializeVocabularyForTesting()

        val finance = DynamicClusterManager.getHierarchicalClusters()["finance"]
        assertNotNull(finance)
        assertTrue(finance!!.keywords.contains("wire"))
        assertTrue(finance.subClusters["banking"]?.keywords?.contains("wire") == true)

        val flattened = DynamicClusterManager.getDynamicClusters()
        assertTrue(flattened["banking"]?.contains("wire") == true)
        assertNotNull(DynamicClusterManager.getSubClusters("finance"))
        assertNull(DynamicClusterManager.getSubClusters("unknown"))

        val matches = DynamicClusterManager.findMatchingClusters(
            listOf("BITCOIN", "transfer", "finance", "banking", "unmatched")
        )
        assertEquals(setOf("finance"), matches.keys)
        val matchedNodes = matches.getValue("finance")
        assertTrue(matchedNodes.contains(finance))
        assertTrue(matchedNodes.any { it.name == "crypto" })
        assertTrue(matchedNodes.any { it.name == "banking" })
    }

    @Test
    fun initializeEnrichesInstalledApplicationsAndIsIdempotent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        DynamicClusterManager.initialize(context)
        val financeNode = DynamicClusterManager
            .getHierarchicalClusters()
            .getValue("finance")
        val financeKeywords = financeNode.keywords.toSet()
        assertTrue(financeKeywords.isNotEmpty())
        assertTrue(DynamicClusterManager.getDynamicClusters().isNotEmpty())

        DynamicClusterManager.initialize(context)
        val repeatedFinanceKeywords = DynamicClusterManager
            .getHierarchicalClusters()
            .getValue("finance")
            .keywords
            .toSet()
        assertEquals(financeKeywords, repeatedFinanceKeywords)
    }

    @Test
    fun initializeCatchesSecurityFailureAndRecoversOnRetry() {
        DynamicClusterManager.initialize(
            contextWithPackageManagerFailure(SecurityException("package access denied for test"))
        )
        assertTrue(DynamicClusterManager.getHierarchicalClusters().isNotEmpty())

        DynamicClusterManager.initialize(InstrumentationRegistry.getInstrumentation().targetContext)
        assertTrue(DynamicClusterManager.getDynamicClusters().isNotEmpty())
    }

    @Test
    fun initializeCatchesMissingPackageManagerAndRecoversOnRetry() {
        val failure = PackageManager.NameNotFoundException("package manager unavailable for test")
        DynamicClusterManager.initialize(contextWithPackageManagerFailure(failure))
        assertTrue(DynamicClusterManager.getHierarchicalClusters().isNotEmpty())

        DynamicClusterManager.initialize(InstrumentationRegistry.getInstrumentation().targetContext)
        assertTrue(DynamicClusterManager.getDynamicClusters().isNotEmpty())
    }

    @Test
    fun clusteringHelpersNormalizeLabelsAndMapCategoryAndPackageSignals() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val rawLabel = context.packageManager.getApplicationLabel(context.applicationInfo).toString()
        assertEquals(rawLabel.lowercase(), applicationLabel(context.packageManager, context.applicationInfo))

        val gameApp = ApplicationInfo().apply {
            category = ApplicationInfo.CATEGORY_GAME
            packageName = "com.example.game"
        }
        assertEquals("game", appDomain(gameApp, gameApp.packageName, "Example Game"))

        val financeApp = ApplicationInfo().apply {
            category = ApplicationInfo.CATEGORY_UNDEFINED
            packageName = "com.chase.mobile"
        }
        assertEquals("finance", appDomain(financeApp, financeApp.packageName, "Chase Banking"))
        assertEquals("finance", inferDomainFromPackageName("com.chase.mobile", "Chase Banking"))
        assertNull(inferDomainFromPackageName("com.example.client", "Generic Application"))
        assertEquals("career", inferDomainFromPackageName("com.jobboard.career", "Career"))
        assertEquals(listOf("chase", "sig"), meaningfulPackageParts("com.chase.sig.android.mobile.app"))
        assertEquals(listOf("chase"), meaningfulPackageParts("com.a.ab.chase.app"))
        assertEquals(
            listOf("DoorDash Driver", "DoorDash", "Driver", "doordash", "driver"),
            appKeywords("DoorDash Driver", listOf("doordash", "driver"))
        )
        assertEquals(listOf("chase"), appKeywords(null, listOf("chase")))
        assertEquals(listOf("chase"), appKeywords("   ", listOf("chase")))
        assertEquals(listOf("AI", "chase"), appKeywords("AI", listOf("chase")))

        val domain = ClusterNode("finance")
        val banking = ClusterNode("banking", mutableSetOf("bank"))
        domain.subClusters["banking"] = banking
        addMatchingSubcategoryKeywords(domain, "com.chase.bank mobile", "Chase", listOf("chase"))
        assertTrue(banking.keywords.containsAll(listOf("bank", "Chase", "chase")))

        val unmatchedDomain = ClusterNode("finance")
        val savings = ClusterNode("savings", mutableSetOf("deposit"))
        unmatchedDomain.subClusters["savings"] = savings
        addMatchingSubcategoryKeywords(unmatchedDomain, "com.example.mobile", null, listOf("example"))
        assertEquals(setOf("deposit"), savings.keywords)

        val nullLabelDomain = ClusterNode("finance")
        val matching = ClusterNode("banking", mutableSetOf("bank"))
        nullLabelDomain.subClusters["banking"] = matching
        addMatchingSubcategoryKeywords(nullLabelDomain, "com.chase.bank", null, listOf("chase"))
        assertEquals(setOf("bank", "chase"), matching.keywords)
    }

    private fun contextWithPackageManagerFailure(failure: Exception): Context {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        return object : ContextWrapper(targetContext) {
            override fun getPackageManager(): PackageManager = throw failure
        }
    }
}
