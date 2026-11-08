package com.chai.aiskin.features.products

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chai.aiskin.core.designsystem.AiSkinTheme
import com.chai.aiskin.core.designsystem.AppCard
import com.chai.aiskin.core.designsystem.AppTopBar
import com.chai.aiskin.core.designsystem.ErrorView
import com.chai.aiskin.core.designsystem.Eyebrow
import com.chai.aiskin.core.designsystem.LoadingView
import com.chai.aiskin.core.designsystem.NoticeBanner
import com.chai.aiskin.core.designsystem.QuietButton
import com.chai.aiskin.core.designsystem.SecondaryButton
import com.chai.aiskin.core.designsystem.Spacing
import com.chai.aiskin.core.designsystem.Tone
import com.chai.aiskin.domain.model.ProductSuggestion
import com.chai.aiskin.domain.model.ProductSuggestions

@Composable
fun ProductsScreen(onBack: () -> Unit, vm: ProductsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(s.message) { s.message?.let { snackbar.showSnackbar(it); vm.messageShown() } }

    Scaffold(
        topBar = {
            AppTopBar("Produk yang cocok", onBack = onBack, actions = {
                if (s.content is ProductsContent.Loaded) {
                    if (s.refreshing) {
                        CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp, color = AiSkinTheme.extended.accent)
                    } else {
                        IconButton(onClick = vm::refresh, modifier = Modifier.testTag("products_refresh")) {
                            Icon(Icons.Outlined.Refresh, "Cari ulang")
                        }
                    }
                }
            })
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when (val c = s.content) {
            ProductsContent.Loading -> LoadingView(
                "AI sedang mencarikan produk yang sesuai dengan hasil analisis kulitmu…\nBiasanya 10–30 detik.",
                Modifier.padding(padding),
            )
            is ProductsContent.Error -> ErrorView(
                title = "Gagal mencari produk",
                message = c.error.message,
                icon = Icons.Outlined.ShoppingBag,
                onRetry = vm::load,
                secondaryLabel = "Kembali", onSecondary = onBack,
                modifier = Modifier.padding(padding),
            )
            is ProductsContent.Loaded -> ProductsBody(c.suggestions, Modifier.padding(padding))
        }
    }
}

@Composable
private fun ProductsBody(s: ProductSuggestions, modifier: Modifier = Modifier) {
    val uri = LocalUriHandler.current
    val open: (String) -> Unit = { url -> if (url.isNotEmpty()) runCatching { uri.openUri(url) } }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screenHorizontal).navigationBarsPadding().testTag("products_list"),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Spacer(Modifier.height(Spacing.xs))
        if (!s.searchGrounded) {
            NoticeBanner(
                "Pencarian internet sedang tidak tersedia, jadi saran ini dari pengetahuan AI. Pastikan produk masih dijual dan variannya sesuai.",
                tone = Tone.Caution, icon = Icons.Outlined.WarningAmber,
            )
        }
        Text(s.summary, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)

        s.products.forEach { ProductCard(it, open) }

        if (s.tips.isNotEmpty()) {
            AppCard {
                Text("Tips memakai produk baru", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(Spacing.xs))
                s.tips.forEach { tip ->
                    Text("• $tip", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }

        if (s.sources.isNotEmpty()) {
            AppCard {
                Text("Sumber pencarian", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(Spacing.xs))
                s.sources.forEach { src ->
                    Row(
                        Modifier.fillMaxWidth().clickable { open(src.url) }.padding(vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(src.title, style = MaterialTheme.typography.bodySmall, color = AiSkinTheme.extended.accent,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, tint = AiSkinTheme.extended.accent, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        NoticeBanner(s.disclaimer, icon = Icons.Outlined.Info)
        Spacer(Modifier.height(Spacing.lg))
    }
}

@Composable
private fun ProductCard(p: ProductSuggestion, open: (String) -> Unit) {
    val ext = AiSkinTheme.extended
    AppCard(modifier = Modifier.testTag("product_card")) {
        Eyebrow("${p.type.label} · ${p.whenToUse}")
        Spacer(Modifier.height(Spacing.xs))
        Text(p.brand, style = MaterialTheme.typography.labelLarge, color = ext.accent)
        Text(p.name, style = MaterialTheme.typography.titleMedium)
        if (p.priceRange.isNotBlank()) {
            Spacer(Modifier.height(Spacing.xxs))
            Text("Kisaran harga: ${p.priceRange}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (p.keyIngredients.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.xxs))
            Text("Bahan utama: ${p.keyIngredients.joinToString(", ")}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(p.whyItFits, style = MaterialTheme.typography.bodyMedium)
        if (p.howToUse.isNotBlank()) {
            Spacer(Modifier.height(Spacing.xs))
            Text("Cara pakai: ${p.howToUse}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (p.cautions.isNotBlank()) {
            Spacer(Modifier.height(Spacing.xs))
            Text("Perhatian: ${p.cautions}", style = MaterialTheme.typography.bodySmall, color = ext.alert)
        }
        Spacer(Modifier.height(Spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            SecondaryButton("Cari di Google", { open(p.searchUrl) }, icon = Icons.Outlined.Search, modifier = Modifier.weight(1f))
            QuietButton("Cek di Shopee", { open(p.shopUrl) })
        }
    }
}
