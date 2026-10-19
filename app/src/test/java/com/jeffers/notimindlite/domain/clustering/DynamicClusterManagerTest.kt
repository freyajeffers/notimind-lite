package com.jeffers.notimindlite.domain.clustering

import android.content.pm.ApplicationInfo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DynamicClusterManagerTest {
    @Before
    fun setUp() {
        DynamicClusterManager.resetForTesting()
    }

    @After
    fun tearDown() {
        DynamicClusterManager.resetForTesting()
    }

    @Test
    fun meaningfulPackageParts_filtersStandardPackagePrefixesAndShortParts() {
        val parts = meaningfulPackageParts("com.chase.sig.android.mobile.app")
        assertEquals(listOf("chase", "sig"), parts)
    }

    @Test
    fun inferDomainFromPackageName_identifiesFinanceDomain() {
        val domain = inferDomainFromPackageName("com.chase.banking", "Chase Mobile")
        assertEquals("finance", domain)
    }

    @Test
    fun inferDomainFromPackageName_identifiesDeliveryDomain() {
        val domain = inferDomainFromPackageName("com.ubereats.delivery", "Uber Eats")
        assertEquals("delivery", domain)
    }

    @Test
    fun inferDomainFromPackageName_returnsNullForUnknownPackage() {
        val domain = inferDomainFromPackageName("org.xyz.foobar", "Widget")
        assertNull(domain)
    }

    @Test
    fun appDomain_mapsOsCategoryWhenDefined() {
        val app = ApplicationInfo().apply {
            category = ApplicationInfo.CATEGORY_GAME
            packageName = "com.supercell.clashofclans"
        }
        val domain = appDomain(app, app.packageName, "Clash of Clans")
        assertEquals("game", domain)
    }

    @Test
    fun appDomain_fallsBackToPackageInferenceWhenCategoryUndefined() {
        val app = ApplicationInfo().apply {
            category = ApplicationInfo.CATEGORY_UNDEFINED
            packageName = "com.crypto.wallet"
        }
        val domain = appDomain(app, app.packageName, "Crypto Wallet")
        assertEquals("finance", domain)
    }

    @Test
    fun appKeywords_combinesLabelAndWordsAndParts() {
        val keywords = appKeywords("DoorDash Driver", listOf("doordash", "driver"))
        assertTrue(keywords.contains("DoorDash Driver"))
        assertTrue(keywords.contains("DoorDash"))
        assertTrue(keywords.contains("Driver"))
        assertTrue(keywords.contains("doordash"))
        assertTrue(keywords.contains("driver"))
    }

    @Test
    fun addMatchingSubcategoryKeywords_populatesMatchingSubClusters() {
        val domainNode = ClusterNode("finance")
        val bankingNode = ClusterNode("banking", keywords = mutableSetOf("bank", "money"))
        val cryptoNode = ClusterNode("crypto", keywords = mutableSetOf("crypto", "bitcoin"))
        domainNode.subClusters["banking"] = bankingNode
        domainNode.subClusters["crypto"] = cryptoNode

        addMatchingSubcategoryKeywords(
            domainNode = domainNode,
            combined = "com.chase.bank mobile banking",
            appLabel = "Chase",
            meaningfulParts = listOf("chase")
        )

        assertTrue(bankingNode.keywords.contains("Chase"))
        assertTrue(bankingNode.keywords.contains("chase"))
        assertTrue(!cryptoNode.keywords.contains("Chase"))
    }

    @Test
    fun dynamicClusterManager_initializesVocabularyAndProvidesClusters() {
        DynamicClusterManager.initializeVocabularyForTesting()

        val hierarchical = DynamicClusterManager.getHierarchicalClusters()
        assertTrue(hierarchical.containsKey("finance"))
        assertTrue(hierarchical.containsKey("delivery"))
        assertTrue(hierarchical.containsKey("security"))

        val financeSubs = DynamicClusterManager.getSubClusters("finance")
        assertNotNull(financeSubs)
        assertTrue(financeSubs!!.containsKey("banking"))
        assertTrue(financeSubs.containsKey("crypto"))
        assertTrue(financeSubs.containsKey("investing"))

        assertNull(DynamicClusterManager.getSubClusters("non_existent_domain"))

        val dynamicFlat = DynamicClusterManager.getDynamicClusters()
        assertTrue(dynamicFlat.containsKey("banking"))
        assertTrue(dynamicFlat["banking"]!!.contains("wire"))

        val matches = DynamicClusterManager.findMatchingClusters(listOf("bitcoin", "transfer"))
        assertTrue(matches.containsKey("finance"))
    }

    @Test
    fun facade_utilDynamicClusterManagerDelegatesCorrectly() {
        DynamicClusterManager.initializeVocabularyForTesting()

        val hierarchical = com.jeffers.notimindlite.util.DynamicClusterManager
            .getHierarchicalClusters()
        assertTrue(hierarchical.containsKey("delivery"))

        val subClusters = com.jeffers.notimindlite.util.DynamicClusterManager
            .getSubClusters("delivery")
        assertNotNull(subClusters)
        assertTrue(subClusters!!.containsKey("food"))

        val matches = com.jeffers.notimindlite.util.DynamicClusterManager
            .findMatchingClusters(listOf("pizza"))
        assertTrue(matches.containsKey("delivery"))
    }
}
