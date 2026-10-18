package com.jeffers.notimindlite.ui.screens

import com.jeffers.notimindlite.data.local.AutoExecuteRule

data class AutoRulesConfig(
    val autoExecuteRules: List<AutoExecuteRule>,
    val rulePackage: String,
    val onRulePackageChange: (String) -> Unit,
    val ruleTitle: String,
    val onRuleTitleChange: (String) -> Unit,
    val ruleAction: String,
    val onRuleActionChange: (String) -> Unit
)
