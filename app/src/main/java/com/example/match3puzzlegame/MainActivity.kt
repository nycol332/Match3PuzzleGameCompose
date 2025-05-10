package com.example.match3puzzlegame

// --- Importuri (Asigură-te că le ai pe toate necesare din pașii anteriori) ---
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon // Pentru iconiță back 
import androidx.compose.material3.IconButton // Pentru iconiță back 
import androidx.compose.material3.Divider // Separator vizual 
import androidx.compose.material3.* // Import Material 3
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.match3puzzlegame.ui.theme.Match3PuzzleGameTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.systemBarsPadding // Importă modifier-ul
import android.content.Context // Pentru a accesa resursele
import android.media.MediaPlayer // Pentru redare audio
import androidx.compose.ui.platform.LocalContext // Pentru a obține contextul în Composable
import androidx.compose.foundation.rememberScrollState // Pentru starea scroll-ului
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll // Pentru modifier-ul de scroll
import androidx.compose.ui.platform.LocalDensity






//Constante si Data classes

data class Recipe(
    val id: Int, // Identificator unic
    val name: String,
    val description: String,
    val ingredientsNeeded: Map<Int, Int>,
    val sellingPrice: Int
)

data class TilePosition(val row: Int, val col: Int)

// Reprezintă un singur obiectiv pentru un nivel
data class LevelObjective(
    val type: ObjectiveType,
    val targetId: Int, // Poate fi IngredientType, RecipeId sau 0 pentru scor
    val targetQuantity: Int // Cantitatea/Numărul necesar
)

// Reprezintă datele complete pentru un nivel
data class LevelData(
    val levelId: Int,
    val name: String,
    val objectives: List<LevelObjective>,
    val maxMoves: Int,
    val unlocksRecipeIds: List<Int> = emptyList() //  Listă de ID-uri rețete deblocate
)

// Tipuri posibile de obiective
enum class ObjectiveType {
    COLLECT_INGREDIENTS, // Colectează un număr specific dintr-un ingredient
    COOK_RECIPES,       // Gătește o rețetă specifică de un număr de ori
    // TODO: Adaugă alte tipuri (ex: CLEAR_BLOCKERS - curăță piese speciale)
}

// --- Structura pentru Upgrade-uri ---
data class UpgradeInfo(
    val id: String, // Folosim String ID pentru flexibilitate (ex: "extra_moves", "faster_cooking")
    val name: String,
    val description: (Int) -> String, // Funcție pentru a genera descrierea bazată pe nivel
    val maxLevel: Int,
    val cost: (Int) -> Int // Funcție pentru a calcula costul nivelului următor (nivel curent -> cost)
)

data class TileMovementInfo(
    val originalRow: Int, // Sau null dacă e nouă
    val finalRow: Int,
    val col: Int,
    val tileType: Int, // Tipul final (pozitiv)
    val isNew: Boolean,
    val fallDistance: Int // Câte rânduri cade (0 dacă nu cade sau e nouă dar pe rândul 0)
)


// --- Constante Globale ---
const val ROWS = 8
const val COLS = 8
const val META_COST = 100
const val EMPTY_TILE = 0 // Important pentru logică
const val TILE_TYPE_1 = 1
const val TILE_TYPE_2 = 2
const val TILE_TYPE_3 = 3
const val TILE_TYPE_4 = 4
const val TILE_TYPE_5 = 5
val TILE_TYPES = listOf(TILE_TYPE_1, TILE_TYPE_2, TILE_TYPE_3, TILE_TYPE_4, TILE_TYPE_5)
private const val TAG = "Match3Game" // TAG pentru Logcat




//Map-uri

// Definirea explicită a tipului Map-ului
val tileColors: Map<Int, Color> = mapOf(
    EMPTY_TILE to Color.Transparent,
    TILE_TYPE_1 to Color.Red.copy(alpha = 0.8f),
    TILE_TYPE_2 to Color(0xFFFFA500), // Orange
    TILE_TYPE_3 to Color.Blue.copy(alpha = 0.8f),
    TILE_TYPE_4 to Color.Green.copy(alpha = 0.8f),
    TILE_TYPE_5 to Color.Magenta.copy(alpha = 0.8f)
)

val tileDrawables: Map<Int, Int> = mapOf(
    TILE_TYPE_1 to R.drawable.castravete, // Înlocuiește cu numele reale ale fișierelor tale!
    TILE_TYPE_2 to R.drawable.rosie,
    TILE_TYPE_3 to R.drawable.ceapa,
    TILE_TYPE_4 to R.drawable.porumb,
    TILE_TYPE_5 to R.drawable.cartof
)




//Liste

// --- Lista de Upgrade-uri Posibile ---
val availableUpgrades = listOf(
    UpgradeInfo(
        id = "extra_moves",
        name = "Mutări Bonus",
        description = { level -> "Începi fiecare nivel cu +$level mutări." },
        maxLevel = 5,
        cost = { level -> 100 * (level + 1) * (level + 1) } // Exemplu: 100, 400, 900, 1600, 2500 Bani
    ),
    UpgradeInfo(
        id = "rare_ingredient_luck",
        name = "Noroc la Ingrediente",
        description = { level -> "Șansă +${5 * level}% să apară ingrediente mai rare." }, // Efectul va fi implementat mai târziu
        maxLevel = 4,
        cost = { level -> 500 * (level + 1) } // Exemplu: 500, 1000, 1500, 2000 Bani
    )
    // TODO: Adaugă upgrade-uri cosmetice, de combustibil, etc.
)



// --- Date Nivele Inițiale ---
val gameLevels = listOf(
    LevelData(
        levelId = 1,
        name = "Piața Locală - Începuturi",
        objectives = listOf(
            LevelObjective(ObjectiveType.COLLECT_INGREDIENTS, TILE_TYPE_2, 15) // Colectează 15 Roșii
        ),
        maxMoves = 20,
        unlocksRecipeIds = listOf(2)
    ),
    LevelData(
        levelId = 2,
        name = "Prima Comandă - Salata",
        objectives = listOf(
            LevelObjective(ObjectiveType.COOK_RECIPES, 1, 1) // Gătește Salata Proaspătă (ID 1) o dată
        ),
        maxMoves = 25,
        unlocksRecipeIds = listOf(3)
    ),
    LevelData(
        levelId = 3,
        name = "Provizia de Iarnă",
        objectives = listOf(
            LevelObjective(ObjectiveType.COLLECT_INGREDIENTS, TILE_TYPE_5, 20), // 20 Cartofi
        ),
        maxMoves = 30,
        unlocksRecipeIds = listOf(4)
    ),
    LevelData(
        levelId = 4,
        name = "Festivalul Recoltei",
        objectives = listOf(
            LevelObjective(ObjectiveType.COOK_RECIPES, 2, 2), // Gătește Garnitura de Porumb de 2 ori
            LevelObjective(ObjectiveType.COLLECT_INGREDIENTS, TILE_TYPE_4, 30) // 30 Porumb
        ),
        maxMoves = 35,
        unlocksRecipeIds = listOf(5) // Deblochează Supa Cremă (4)
    )


    // Adaugă mai multe nivele
)

val allPossibleRecipes = listOf(
    Recipe(
        id = 1,
        name = "Salată Proaspătă",
        description = "Perfectă pentru o zi de vară.",
        ingredientsNeeded = mapOf(TILE_TYPE_1 to 5, TILE_TYPE_2 to 3, TILE_TYPE_3 to 2), // 5 Castraveți, 3 Roșii, 2 Cepe
        sellingPrice = 30
    ),
    Recipe(
        id = 2,
        name = "Garnitură de Porumb",
        description = "Simplu și gustos.",
        ingredientsNeeded = mapOf(TILE_TYPE_4 to 8, TILE_TYPE_1 to 2), // 8 Porumb, 2 Castraveți
        sellingPrice = 45
    ),
    Recipe(
        id = 3,
        name = "Tocăniță de Legume",
        description = "Sățioasă și aromată.",
        ingredientsNeeded = mapOf(TILE_TYPE_5 to 6, TILE_TYPE_2 to 4, TILE_TYPE_3 to 3), // 6 Cartofi, 4 Roșii, 3 Cepe
        sellingPrice = 70
        ),
    // --- Adaugă mai multe rețete aici ---
    Recipe(
        id = 4,
        name = "Supă Cremă de Roșii",
        description = "Clasică și reconfortantă.",
        ingredientsNeeded = mapOf(TILE_TYPE_2 to 10, TILE_TYPE_3 to 4), // 10 Roșii, 4 Cepe
        sellingPrice = 55
        ),
    Recipe(
        id = 5,
        name = "Cartofi la Cuptor",
        description = "Cu ierburi aromatice.",
        ingredientsNeeded = mapOf(TILE_TYPE_5 to 12, TILE_TYPE_3 to 2), // 12 Cartofi, 2 Cepe
        sellingPrice = 60
        )
)
val initialRecipes = allPossibleRecipes.filter { it.id == 1 }





//Funcții globale pure

fun getIngredientName(tileType: Int): String {
    return when (tileType) {
        TILE_TYPE_1 -> "Cucumber" // Exemplu
        TILE_TYPE_2 -> "Tomato" // Exemplu
        TILE_TYPE_3 -> "Onion" // Exemplu
        TILE_TYPE_4 -> "Corn" // Exemplu
        TILE_TYPE_5 -> "Potato" // Exemplu
        else -> "Necunoscut"
    }
}

// --- Funcție Helper pentru Redare Sunet ---
private fun playSound(context: Context, soundResourceId: Int) {
    // Folosește try-catch pentru a evita crash-uri dacă resursa nu e găsită sau apare altă eroare
    try {
        // Creează un MediaPlayer nou PENTRU FIECARE redare a unui sunet scurt.
        // Nu refolosi același obiect MediaPlayer pentru sunete scurte rapide,
        // poate cauza probleme de suprapunere sau întârzieri.
        val mp = MediaPlayer.create(context, soundResourceId)
        if (mp == null) {
            Log.e(TAG, "playSound: MediaPlayer.create returned null for resource ID: $soundResourceId")
            return
        }
        mp.setOnCompletionListener { mediaPlayer ->
            // Eliberează resursele MediaPlayer DUPĂ ce sunetul s-a terminat
            mediaPlayer?.release()
            Log.d(TAG, "playSound: MediaPlayer released for resource ID: $soundResourceId")
        }
        mp.setOnErrorListener { _, what, extra ->
            Log.e(TAG, "playSound: MediaPlayer error! what: $what, extra: $extra for resource ID: $soundResourceId")
            // Încearcă să eliberezi resursele și în caz de eroare
            mp?.release()
            true // Indică faptul că am gestionat eroarea
        }
        mp.start() // Pornește redarea
    } catch (e: Exception) {
        Log.e(TAG, "playSound: Exception while trying to play sound ID: $soundResourceId", e)
    }
}






// --- MainActivity  ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Match3PuzzleGameTheme {
                Match3GameApp() // Apelăm Composable-ul principal care deține starea
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpgradesScreen(
    allPossibleUpgrades: List<UpgradeInfo>, // Lista tuturor upgrade-urilor definite global
    currentOwnedUpgrades: Map<String, Int>, // Map<UpgradeId, CurrentLevel> de la player
    currentPlayerMoney: Int, // Banii actuali ai jucătorului
    onPurchaseUpgrade: (upgradeId: String) -> Unit, // Callback la cumpărare
    onClose: () -> Unit // Callback pentru a închide ecranul
) {
    val context = LocalContext.current // Pentru sunete
    Scaffold( // Folosim Scaffold pentru un TopAppBar și o structură mai bună
        topBar = {
            TopAppBar(
                title = { Text("Atelier Îmbunătățiri") },
                navigationIcon = {
                    IconButton(onClick = {
                        playSound(context, R.raw.click)
                        onClose()
                    }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Înapoi la Joc")
                    }
                },
                actions = { // Acțiuni în dreapta (ex: afișare bani)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.coin),
                            contentDescription = "Bani",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = currentPlayerMoney.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            )
        }
    ) { innerPadding -> // Padding-ul oferit de Scaffold pentru conținut
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding) // Aplică padding-ul de la Scaffold
                .padding(horizontal = 16.dp, vertical = 8.dp), // Padding suplimentar pentru conținut
            verticalArrangement = Arrangement.spacedBy(10.dp) // Spațiu între carduri
        ) {
            if (allPossibleUpgrades.isEmpty()){
                item {
                    Text(
                        "Nicio îmbunătățire disponibilă momentan.",
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }

            items(allPossibleUpgrades, key = { it.id }) { upgradeInfo ->
                val currentLevel = currentOwnedUpgrades[upgradeInfo.id] ?: 0
                val isMaxLevel = currentLevel >= upgradeInfo.maxLevel
                val costForNextLevel = if (!isMaxLevel) upgradeInfo.cost(currentLevel) else 0
                val canAfford = currentPlayerMoney >= costForNextLevel && !isMaxLevel

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(upgradeInfo.name, style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "Nivel: $currentLevel / ${upgradeInfo.maxLevel}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Efect Curent: ${upgradeInfo.description(currentLevel)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        if (isMaxLevel) {
                            Text(
                                "NIVEL MAXIM ATINS",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.align(Alignment.End)
                            )
                        } else {
                            Text(
                                "Următorul Nivel: ${upgradeInfo.description(currentLevel + 1)}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = {
                                    playSound(context, R.raw.click)
                                    onPurchaseUpgrade(upgradeInfo.id)
                                },
                                enabled = canAfford,
                                modifier = Modifier.align(Alignment.End),
                                colors = ButtonDefaults.buttonColors(
                                    // Culori diferite dacă nu își permite
                                    containerColor = if (canAfford) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (canAfford) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Text("Îmbunătățește (${costForNextLevel} Bani)")
                            }
                        }
                    }
                }
            }
        } // Sfârșit LazyColumn
    } // Sfârșit Scaffold
}
// --- Composable Părinte care Deține Starea și Logica ---
@Composable
fun Match3GameApp() {
    // === STAREA JOCULUI  ===
    var inventory by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    var feedbackMessage by remember { mutableStateOf("") }
    // Inițializare board folosind funcția DUPĂ ce e definită
    var board by remember { mutableStateOf(emptyList<MutableList<Int>>()) } // Inițial goală
    var selectedTilePos by remember { mutableStateOf<TilePosition?>(null) }
    var tilesBeingMatched by remember { mutableStateOf<Set<TilePosition>>(emptySet()) }
    var isProcessing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var swappingTiles by remember { mutableStateOf<Pair<TilePosition, TilePosition>?>(null) }
    val tile1Offset = remember { Animatable(IntOffset.Zero, IntOffset.VectorConverter) }
    val tile2Offset = remember { Animatable(IntOffset.Zero, IntOffset.VectorConverter) }
    var swapAnimationFinished by remember { mutableStateOf(true) }
    var currentLevelIndex by remember { mutableStateOf(0) }
    val currentLevelData = remember(currentLevelIndex) { gameLevels.getOrNull(currentLevelIndex) }
    var movesLeft by remember { mutableStateOf(0) } // Va fi setat în LaunchedEffect
    var objectiveProgress by remember { mutableStateOf<Map<LevelObjective, Int>>(emptyMap()) }
    var gameState by remember { mutableStateOf("Playing") }
    var selectedRecipeToShow by remember { mutableStateOf<Recipe?>(null) } // Pentru dialog
    var showRecipeBookScreen by remember { mutableStateOf(false) } // Pentru navigare ecran
    var availableRecipes by remember { mutableStateOf(initialRecipes.toMutableList()) } // Lista rețetelor
    val context = LocalContext.current // Obține contextul aici
    var playerXP by remember { mutableStateOf(0) } // --- Starea pentru Experiență ---
    var playerMoney by remember { mutableStateOf(100) }
    var cookedMealsInventory by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }  // --- Stare pentru Mâncarea Gătită, gata de vânzare ---
    var showShopDialog by remember { mutableStateOf(false) }
    var showUpgradesScreen by remember { mutableStateOf(false) }// --- Stare pentru ecranul de upgrade-uri ---
    val density = LocalDensity.current // *NOU* Obține densitatea ecranului
    var currentTileMovements by remember { mutableStateOf<List<TileMovementInfo>>(emptyList()) }
    var playerUpgrades by remember { mutableStateOf<Map<String, Int>>(emptyMap()) } // Starea pentru nivelul upgrade-urilor


    // === LOGICA JOCULUI  ===


    // --- Funcția pentru Cumpărarea/Îmbunătățirea unui Upgrade ---
    fun purchaseUpgrade(upgradeId: String) {
        val upgradeInfo = availableUpgrades.find { it.id == upgradeId }
        if (upgradeInfo == null) {
            Log.e(TAG, "Attempted to purchase unknown upgrade ID: $upgradeId")
            feedbackMessage = "Eroare: Îmbunătățire necunoscută!"
            playSound(context, R.raw.lost) // Sunet de eroare/negare
            return
        }

        val currentLevel = playerUpgrades[upgradeId] ?: 0 // Nivelul actual (0 dacă nu e deținut)

        if (currentLevel >= upgradeInfo.maxLevel) {
            Log.d(TAG, "Upgrade ${upgradeInfo.name} is already at max level.")
            feedbackMessage = "${upgradeInfo.name} este deja la Nivel Maxim!"
            // playSound(context, R.raw.click) // Poate un sunet neutru
            return
        }

        val costForNextLevel = upgradeInfo.cost(currentLevel) // Costul pentru a trece la nivelul currentLevel + 1

        if (playerMoney >= costForNextLevel) {
            // Jucătorul își permite
            playerMoney -= costForNextLevel // Scade banii
            val newLevel = currentLevel + 1
            // Actualizează starea playerUpgrades
            // Important: creăm un map NOU pentru a declanșa recompoziția corect
            playerUpgrades = playerUpgrades + (upgradeId to newLevel)

            Log.i(TAG, "Purchased/Upgraded ${upgradeInfo.name} to level $newLevel for $costForNextLevel money. Money left: $playerMoney")
            feedbackMessage = "${upgradeInfo.name} a ajuns la Nivelul $newLevel!" // Mesaj de succes
            playSound(context, R.raw.coin) // Sunet de "cumpărare" / succes

            // Logica specifică efectului (dacă e cazul să se aplice imediat)
            // Pentru "Mutări Extra", efectul se va aplica la următorul start de nivel.
            Log.d(TAG, "Upgrade '${upgradeInfo.name}' purchased. Effect will apply as needed.")

        } else {
            // Nu își permite
            Log.d(TAG, "Cannot afford upgrade ${upgradeInfo.name}. Needed: $costForNextLevel, Has: $playerMoney")
            feedbackMessage = "Nu ai ${costForNextLevel} Bani pentru ${upgradeInfo.name}!"
            playSound(context, R.raw.lost) // Sunet de eroare/negare
        }
    }
    // ---  Funcție pentru Vânzarea Mâncărurilor ---
    fun sellCookedMeals() {
        if (cookedMealsInventory.isEmpty()) {
            Log.d(TAG, "Sell attempt but cooked inventory is empty.")
            feedbackMessage = "Nu ai ce vinde!"
            return
        }

        var moneyEarned = 0
        cookedMealsInventory.forEach { (recipeId, quantity) ->
            // Găsește prețul rețetei
            val recipe = allPossibleRecipes.find { it.id == recipeId } // Folosim lista globală
            if (recipe != null) {
                moneyEarned += recipe.sellingPrice * quantity
            } else {
                Log.w(TAG, "Could not find recipe data for ID $recipeId during selling.")
            }
        }

        Log.d(TAG, "Selling all cooked meals. Earned: $moneyEarned Money.")

        // Actualizează banii jucătorului
        playerMoney += moneyEarned

        // Golește inventarul de mâncare gătită
        cookedMealsInventory = emptyMap()

        // Oferă feedback
        feedbackMessage = "Ai vândut marfa pentru $moneyEarned Bani!"

        // TODO: Redă un sunet de "casa de marcat" sau similar
        playSound(context, R.raw.coin) // Folosim sunetul de monedă existent? Sau altul?
    }



    fun findMatchesOnBoard(targetBoard: List<List<Int>>): Set<TilePosition> {
        val matches = mutableSetOf<TilePosition>()
        for (r in 0 until ROWS) {
            var currentStreak = 1
            var currentType = -1 // Tip invalid inițial
            for (c in 0 until COLS) {
                val tileType =
                    targetBoard.getOrNull(r)?.getOrNull(c) ?: EMPTY_TILE // Folosește targetBoard
                if (tileType != EMPTY_TILE && tileType == currentType) {
                    currentStreak++
                } else {
                    if (currentStreak >= 3) {
                        for (i in 1..currentStreak) {
                            matches.add(TilePosition(r, c - i))
                        }
                    }
                    currentType = tileType
                    currentStreak = if (tileType != EMPTY_TILE) 1 else 0
                }
            }
            if (currentStreak >= 3) {
                for (i in 1..currentStreak) {
                    matches.add(TilePosition(r, COLS - i))
                }
            }
        }
        // Verificare Verticală
        for (c in 0 until COLS) {
            var currentStreak = 1
            var currentType = -1
            for (r in 0 until ROWS) {
                val tileType =
                    targetBoard.getOrNull(r)?.getOrNull(c) ?: EMPTY_TILE // Folosește targetBoard
                if (tileType != EMPTY_TILE && tileType == currentType) {
                    currentStreak++
                } else {
                    if (currentStreak >= 3) {
                        for (i in 1..currentStreak) {
                            matches.add(TilePosition(r - i, c))
                        }
                    }
                    currentType = tileType
                    currentStreak = if (tileType != EMPTY_TILE) 1 else 0
                }
            }
            if (currentStreak >= 3) {
                for (i in 1..currentStreak) {
                    matches.add(TilePosition(ROWS - i, c))
                }
            }
        }
        return matches
    }


    fun generateValidInitialBoard(): List<MutableList<Int>> {
        var attempts = 0
        while (attempts < 100) { // Adăugăm o limită de siguranță pentru a evita bucle infinite
            Log.d(TAG, "Generating initial board attempt: ${attempts + 1}")
            // 1. Generează o tablă candidată
            val candidateBoard = List(ROWS) {
                MutableList(COLS) { TILE_TYPES.random() }
            }

            // 2. Verifică potrivirile pe tabla candidată
            val initialMatches = findMatchesOnBoard(candidateBoard)

            // 3. Dacă nu sunt potriviri, returnează tabla validă
            if (initialMatches.isEmpty()) {
                Log.d(TAG, "Valid initial board found after ${attempts + 1} attempts.")
                return candidateBoard // Am găsit o tablă bună!
            }

            // 4. Dacă există potriviri, bucla continuă și generăm alta
            attempts++
            Log.d(TAG, "Initial board had matches, retrying...")
        }
        // Fallback: Dacă nu găsim o tablă validă după multe încercări,
        // returnăm ultima generată (cu potriviri) pentru a evita blocarea.
        Log.w(
            TAG,
            "Could not generate a match-free initial board after 100 attempts. Using last generated board."
        )
        // Să returnăm totuși o tablă goală în acest caz extrem pentru a fi clar
        return List(ROWS) { MutableList(COLS) { EMPTY_TILE } } // Sau returnează ultima `candidateBoard`
    }


    // Inițializarea reală a tablei DUPĂ definirea funcțiilor necesare
    LaunchedEffect(Unit) { // Rulează o singură dată la început
        board = generateValidInitialBoard()
    }


    fun checkLevelEndCondition(progressToCheck: Map<LevelObjective, Int> = objectiveProgress) {
        if (gameState != "Playing") return // Nu verifica dacă jocul s-a terminat deja

        if (currentLevelData == null) {
            Log.w(TAG, "checkLevelEndCondition called with null level data!")
            return
        }

        // --- LOG ÎNCEPUT VERIFICARE ---
        Log.d(TAG, "--- Checking Level End Condition for Level ${currentLevelData.levelId} ---")
        // --- LOG STARE PROGRES ACTUAL ---
        Log.d(TAG, "Checking with Progress State: $progressToCheck")

        // Verifică dacă TOATE obiectivele sunt îndeplinite
        var allMet = true // Presupunem adevărat inițial
        currentLevelData.objectives.forEach { objective -> // Iterează prin fiecare obiectiv al nivelului
            val progress = progressToCheck[objective] ?: 0
            val target = objective.targetQuantity
            val isMet = progress >= target
            Log.d(
                TAG,
                "Checking Objective: Type=${objective.type}, TargetID=${objective.targetId}, TargetQty=$target, CurrentProgress=$progress, IsMet=$isMet"
            )
            if (!isMet) {
                allMet = false
            }
        }

        // --- LOG REZULTAT FINAL VERIFICARE OBIECTIVE ---
        Log.d(TAG, "Final check: All Objectives Met = $allMet")

        // --- Restul logicii ---
        if (allMet) {
            // --- CONDIȚIE DE VICTORIE ---
            Log.i(TAG, "Level ${currentLevelData.levelId} WON!")
            gameState = "Won"

            // --- Deblochează Rețete ---
            val newlyUnlockedRecipes = mutableListOf<String>()
            currentLevelData.unlocksRecipeIds.forEach { recipeId ->
                Log.d(TAG, "Checking unlock for Recipe ID: $recipeId") // Log existent, e ok
                if (availableRecipes.none { it.id == recipeId }) {
                    Log.d(TAG, "Recipe ID $recipeId is NOT already available.") // Log existent, e ok
                    allPossibleRecipes.find { it.id == recipeId }?.let { recipeToAdd ->
                        Log.d(TAG, "Found recipe to add: ${recipeToAdd.name}") // Log existent, e ok
                        availableRecipes = (availableRecipes + recipeToAdd).toMutableList()
                        newlyUnlockedRecipes.add(recipeToAdd.name)
                        Log.i(TAG, "Unlocked recipe: ${recipeToAdd.name}. New available list size: ${availableRecipes.size}") // Log existent, e ok
                    } ?: Log.w(TAG, "Recipe ID $recipeId to unlock not found in allPossibleRecipes!") // Log existent, e ok
                } else {
                    Log.d(TAG, "Recipe ID $recipeId IS already available. Skipping.") // Log existent, e ok
                }
            }

            var winMessage = "Nivel ${currentLevelData.levelId} Terminat!"
            if (newlyUnlockedRecipes.isNotEmpty()) {
                winMessage += "\nRețete noi: ${newlyUnlockedRecipes.joinToString()}"
            }
            feedbackMessage = winMessage // Setează mesajul de victorie
            playSound(context, R.raw.win) // Redă sunetul de victorie

        } else if (movesLeft <= 0) {
            // --- CONDIȚIE DE ÎNFRÂNGERE ---
            Log.i(TAG, "Level ${currentLevelData.levelId} LOST! No moves left.")
            gameState = "Lost"
            playSound(context, R.raw.lost) // Redă sunetul de înfrângere
            feedbackMessage = "Ai rămas fără mutări! Reîncearcă!"

        } else {
            // --- Nivelul Continuă ---
            // Am eliminat logul de aici pentru că îl avem mai sus ("Final check: All Objectives Met = false")
            // Log.d(TAG, "Level continues. Moves left: $movesLeft. Objectives met: $allMet")
        }
    }


    // --- Funcție care gestionează logica click-ului pe piesă ---
    fun handleTileClick(row: Int, col: Int) {
        // Pune TOATĂ logica din vechiul onTileClick aici
        if (isProcessing || !swapAnimationFinished || gameState != "Playing") {
            Log.d(
                TAG,
                "Click ignorat: processing=$isProcessing, animFinished=$swapAnimationFinished, state=$gameState"
            )
            // Folosim 'return' simplu pentru a ieși din funcție
            return
        }

        val clickedPos = TilePosition(row, col)
        // --- Accesăm direct starea din Match3GameApp ---
        val currentSelection = selectedTilePos

        Log.d(TAG, "handleTileClick: ($row, $col). Current selection: $currentSelection")
        playSound(context, R.raw.click) // Sunet click piesă

        // --- Funcție Helper pentru Adiacență ---
        fun areAdjacent(pos1: TilePosition, pos2: TilePosition): Boolean {
            val rowDiff = abs(pos1.row - pos2.row)
            val colDiff = abs(pos1.col - pos2.col)
            return (rowDiff == 1 && colDiff == 0) || (rowDiff == 0 && colDiff == 1)
        }
        if (currentSelection == null) {
            selectedTilePos = clickedPos // Modifică starea direct
            feedbackMessage = "Selectat: ($row, $col)"
        } else {
            if (clickedPos == currentSelection) {
                selectedTilePos = null
                feedbackMessage = "Deselectat"
            } else if (areAdjacent(currentSelection, clickedPos)) { // Apelează funcția helper
                // --- SWAP INIȚIAT ---
                Log.d(TAG, "Adjacent click detected. Initiating swap attempt.")

                // Consumă mutarea
                if (movesLeft > 0) {
                    movesLeft = movesLeft - 1 // Modifică starea direct
                    Log.d(TAG, "Move consumed on swap attempt. Moves left NOW: $movesLeft")

                    // Inițiază animația
                    selectedTilePos = null
                    feedbackMessage = "Schimbare..."
                    swappingTiles = Pair(currentSelection, clickedPos) // Modifică starea direct
                    swapAnimationFinished = false // Modifică starea direct

                } else {
                    Log.d(TAG, "Attempted swap but no moves left.")
                    feedbackMessage = "Fără mutări!"
                    selectedTilePos = null
                    checkLevelEndCondition() // Apelează funcția helper
                }
            } else { // Click neadiacent
                selectedTilePos = clickedPos
                feedbackMessage = "Selectat: ($row, $col)"
            }
        }
    }



    fun calculateGravityAndFill(currentBoardState: List<List<Int>>): Pair<List<MutableList<Int>>, List<TileMovementInfo>> {
        val numRows = currentBoardState.size
        val numCols = currentBoardState.firstOrNull()?.size ?: 0
        if (numCols == 0) return Pair(emptyList(), emptyList()) // Tablă goală

        val finalBoardState = List(numRows) { MutableList(numCols) { EMPTY_TILE } }
        val movements = mutableListOf<TileMovementInfo>()

        for (c in 0 until numCols) {
            var writeRow = numRows - 1 // Unde scriem următoarea piesă care cade (începem de jos)
            // Procesăm coloana de jos în sus pentru gravitație
            for (r in numRows - 1 downTo 0) {
                val currentType = currentBoardState[r][c]
                if (currentType != EMPTY_TILE) {
                    val fallDistance = writeRow - r
                    if (writeRow != r) { // A căzut
                        movements.add(TileMovementInfo(r, writeRow, c, currentType, false, fallDistance))
                    } else { // Nu a căzut
                        movements.add(TileMovementInfo(r, r, c, currentType, false, 0)) // O adăugăm oricum pentru a o avea în starea finală
                    }
                    finalBoardState[writeRow][c] = currentType
                    writeRow-- // Mergem la rândul de deasupra pentru următoarea piesă
                }
            }
            // Umple spațiile goale rămase sus cu piese noi
            for (r in writeRow downTo 0) {
                val newType = TILE_TYPES.random()
                finalBoardState[r][c] = newType
                movements.add(TileMovementInfo(-1, r, c, newType, true, r + 1)) // OriginalRow -1 pt nou, fallDistance mare
            }
        }
        return Pair(finalBoardState, movements) // Returnăm o listă imutabilă
    }


    suspend fun processMatchesAndCascades() {
        var currentBoardInternal = board // Folosim o copie internă pt logica buclei
        var cascadeMultiplier = 1.0
        var cascadeCount = 0

        Log.d(TAG, ">>> Starting Cascade Processing <<<")

        while(true) { // Bucla pentru cascade multiple
            Log.d(TAG, "-- Checking for matches on internal board --")
            val matches = findMatchesOnBoard(currentBoardInternal)

            // --- PASUL 0: Verificare Oprire Cascadă ---
            if (matches.isEmpty()) {
                Log.d(TAG, "No more matches found. Finishing cascade sequence.")
                // Afișare scor final al turei (dacă există)
                // Verifică condiția de final nivel (victorie/înfrângere) ACUM
                checkLevelEndCondition()
                break // Ieși din bucla while
            }

            // --- PASUL 1: Procesare Potrivire Curentă (Scor, Inventar, etc.) ---
            cascadeCount++
            Log.d(TAG, "Cascade $cascadeCount: Found ${matches.size} matched tiles.")
            // Calculează punctele, ingredientele câștigate din 'matches' și 'currentBoardInternal'
            var basePointsThisMatch = 0
            val ingredientsEarnedThisMatch = mutableMapOf<Int, Int>()
            matches.forEach { pos ->
                // Folosim currentBoardInternal pentru a citi tipul piesei ÎNAINTE de a o goli
                if (pos.row in 0 until ROWS && pos.col in 0 until COLS) {
                    val tileType = currentBoardInternal.getOrNull(pos.row)?.getOrNull(pos.col)
                    if (tileType != null && tileType != EMPTY_TILE) {
                        // Adaugă la ingrediente
                        ingredientsEarnedThisMatch[tileType] =
                            ingredientsEarnedThisMatch.getOrDefault(tileType, 0) + 1
                        // Adaugă puncte de bază
                        basePointsThisMatch += 10 // 10 puncte per piesă
                    }
                }

            }
            // Aplică bonusuri și multiplicator
            if (matches.size >= 5) basePointsThisMatch += 100
            else if (matches.size == 4) basePointsThisMatch += 50

            // Actualizează inventarul de ingrediente
            if (ingredientsEarnedThisMatch.isNotEmpty()) {
                val currentInventory = inventory.toMutableMap()
                ingredientsEarnedThisMatch.forEach { (ingredientId, quantity) ->
                    currentInventory[ingredientId] =
                        currentInventory.getOrDefault(ingredientId, 0) + quantity
                }
                inventory = currentInventory // Actualizează starea inventarului
                Log.d(TAG, "Inventory updated: $inventory") // Log adăugat anterior
            }

            // Actualizează progresul obiectivelor de COLECTARE
            if (ingredientsEarnedThisMatch.isNotEmpty()) {
                val updatedProgressCollect = objectiveProgress.toMutableMap()
                currentLevelData?.objectives?.forEach { objective ->
                    if (objective.type == ObjectiveType.COLLECT_INGREDIENTS) {
                        val ingredientId = objective.targetId
                        val collectedNow =
                            ingredientsEarnedThisMatch.getOrDefault(ingredientId, 0)
                        if (collectedNow > 0) {
                            val currentProg = updatedProgressCollect[objective] ?: 0
                            updatedProgressCollect[objective] =
                                (currentProg + collectedNow).coerceAtMost(objective.targetQuantity)
                        }
                    }
                }
                objectiveProgress = updatedProgressCollect // Aplică actualizările
            }

            val feedbackParts = ingredientsEarnedThisMatch.map { "+${it.value} ${getIngredientName(it.key)}" }
            // Setează mesajul de feedback
            feedbackMessage = if (cascadeCount > 1) {
                "Cascadă $cascadeCount! ${feedbackParts.joinToString()} "
            } else {
                "Potrivire! ${feedbackParts.joinToString()} "
            }

            // Crește multiplicatorul pentru următoarea cascadă
            cascadeMultiplier += 0.5

            // --- PASUL 2: Animație Dispariție ---
            Log.d(TAG, "Cascade $cascadeCount: Starting disappear animation.")
            tilesBeingMatched = matches // Informează GameTile să animeze dispariția
            delay(400L) // Așteaptă animația de dispariție
            tilesBeingMatched = emptySet() // Oprește efectul vizual de dispariție

            // --- PASUL 3: Pregătire pentru Cădere ---
            // 3a. Creează o reprezentare logică a tablei CU spații goale
            val boardWithEmptyTiles = currentBoardInternal.map { it.toMutableList() }
            matches.forEach { pos -> boardWithEmptyTiles[pos.row][pos.col] = EMPTY_TILE }

            // --- Actualizăm starea 'board' ACUM cu golurile ---
            // GameBoard va desena acum goluri în locurile pieselor dispărute
            board = boardWithEmptyTiles
            currentBoardInternal = boardWithEmptyTiles // Continuăm logica cu această stare
            Log.d(TAG, "Cascade $cascadeCount: Board updated with empty tiles.")

            // 3b. Calculează starea finală și mișcările necesare DUPĂ cădere/umplere
            val (finalBoardState, tileMovements) = calculateGravityAndFill(currentBoardInternal) // Calculăm pe baza tablei cu goluri
            if (tileMovements.isEmpty()) {
                Log.d(TAG, "Cascade $cascadeCount: No tiles moved or fell. Checking for matches again.")
                // Dacă doar au dispărut (ex: rândul de sus) și nimic nu cade,
                // trebuie doar să actualizăm starea finală și să continuăm bucla
                board = finalBoardState // Asigură că e starea corectă fără piese EMPTY
                currentBoardInternal = finalBoardState
                continue // Treci la următoarea iterație a buclei while
            }
            Log.d(TAG, "Cascade $cascadeCount: Calculated ${tileMovements.size} tile movements.")

            // --- PASUL 4: Animație Cădere/Apariție ---
            Log.d(TAG, "Cascade $cascadeCount: Starting fall/appear animation.")
            // 4a. Informează GameBoard despre mișcări (va porni animațiile în GameBoard)
            currentTileMovements = tileMovements
            // !!! NU actualizăm 'board' la finalBoardState AICI !!!

            // 4b. Așteaptă suficient timp pentru ca animațiile gestionate de GameBoard să se termine
            val maxFallDelay = 30L * COLS + 10L * ROWS
            val fallAnimDuration = 350L
            val totalWaitTime = fallAnimDuration + maxFallDelay + 150L // Mărit bufferul puțin
            Log.d(TAG, "Cascade $cascadeCount: Waiting ${totalWaitTime}ms for fall animations...")
            delay(totalWaitTime)
            Log.d(TAG, "Cascade $cascadeCount: Fall animations assumed finished.")


            // --- PASUL 5: Actualizare Finală Stare Logică ---
            Log.d(TAG, "Cascade $cascadeCount: Applying final board state.")
            board = finalBoardState // Actualizează starea logică la configurația finală

            // Golește informațiile de mișcare (animațiile s-au terminat)
            currentTileMovements = emptyList()
            Log.d(TAG, "Cascade $cascadeCount: Cleared tile movements.")

            // Pregătește următoarea iterație a buclei while cu starea finală
            currentBoardInternal = finalBoardState

            Log.d(TAG, "--- End of Cascade $cascadeCount Iteration ---")
        }

        Log.d(TAG, ">>> Cascade Processing Finished <<<")

    }



    fun performValidSwapAndProcess(pos1: TilePosition, pos2: TilePosition) {
        Log.d(TAG, "Performing valid swap logic for $pos1, $pos2")
        // Actualizează starea reală a tablei
        val newBoard = board.map { it.toMutableList() }
        val temp = newBoard[pos1.row][pos1.col]
        newBoard[pos1.row][pos1.col] = newBoard[pos2.row][pos2.col]
        newBoard[pos2.row][pos2.col] = temp
        board = newBoard

        // Pornește procesarea logică
        isProcessing = true
        feedbackMessage = ""
        scope.launch { // Lansăm procesarea într-o corutină separată
            processMatchesAndCascades()
            checkLevelEndCondition()
            isProcessing = false
            Log.d(TAG, "Valid swap processing finished.")
        }
    }

    // --- Funcție Helper pentru Verificare Gătit ---
    fun canCookRecipe(recipe: Recipe): Boolean {
        // Verifică fiecare ingredient necesar
        recipe.ingredientsNeeded.forEach { (ingredientId, quantityNeeded) ->
            val quantityOwned = inventory.getOrDefault(ingredientId, 0) // Cât deține jucătorul
            if (quantityOwned < quantityNeeded) {
                // Dacă lipsește chiar și un singur ingredient, nu se poate găti
                return false
            }
        }
        // Dacă am ajuns aici, înseamnă că toate ingredientele sunt suficiente
        return true
    }


    fun cookRecipe(recipe: Recipe) {
        if (!canCookRecipe(recipe)) {
            Log.w(TAG, "Attempted to cook ${recipe.name} without enough ingredients!")
            feedbackMessage = "Nu ai suficiente ingrediente pentru ${recipe.name}!"
            return // Nu continua dacă nu se poate găti
        }

        Log.d(TAG, "Cooking recipe: ${recipe.name}")

        // 1. Consumă ingredientele
        val updatedInventory = inventory.toMutableMap() // Copie mutabilă
        recipe.ingredientsNeeded.forEach { (ingredientId, quantityNeeded) ->
            val currentQuantity = updatedInventory.getOrDefault(ingredientId, 0)
            // Scade cantitatea necesară (asigură-te că nu scazi sub 0, deși canCook a verificat)
            updatedInventory[ingredientId] = (currentQuantity - quantityNeeded).coerceAtLeast(0)
        }
        inventory = updatedInventory // Actualizează starea inventarului
        playSound(context, R.raw.gatire) // --- Redă sunetul de gătit ---

        val xpGained = 50 // Exemplu: 50 XP per rețetă
        playerXP += xpGained
        Log.d(TAG, "Gained $xpGained XP. Total XP: $playerXP")

        // ---  Acordă Monedă ---
        val currentCookedAmount = cookedMealsInventory.getOrDefault(recipe.id, 0)
        val updatedCookedInventory = cookedMealsInventory.toMutableMap()
        updatedCookedInventory[recipe.id] = currentCookedAmount + 1
        cookedMealsInventory = updatedCookedInventory // Actualizează starea cookedMealsInventory
        Log.d(TAG, "Added ${recipe.name} to cooked inventory. New cooked inv: $cookedMealsInventory")

        val updatedProgress = objectiveProgress.toMutableMap() // Copie curentă
        currentLevelData?.objectives?.forEach { objective ->
            if (objective.type == ObjectiveType.COOK_RECIPES && objective.targetId == recipe.id) {
                val currentProg = updatedProgress[objective] ?: 0
                updatedProgress[objective] = (currentProg + 1).coerceAtMost(objective.targetQuantity)
                Log.d(TAG, "Cook objective progress for ${recipe.name}: ${updatedProgress[objective]}/${objective.targetQuantity}")
            }
        }
        objectiveProgress = updatedProgress
        feedbackMessage = "Ai gătit ${recipe.name}! +$xpGained XP"
        selectedRecipeToShow = null
        checkLevelEndCondition(updatedProgress) // Pasează map-ul actualizat
    }

    // ---  Funcție pentru a trece la nivelul următor ---
    fun goToNextLevel() {
        Log.d(TAG, "goToNextLevel called. Current index: $currentLevelIndex")
        playSound(context, R.raw.click) // Sunetul aici
        if (currentLevelIndex < gameLevels.size - 1) {
            currentLevelIndex++ // Incrementează starea
            Log.d(TAG, "Moving to next level index: $currentLevelIndex")
        } else {
            Log.d(TAG, "All levels finished!")
            gameState = "Finished" // Setează starea de final joc
            feedbackMessage = "Felicitări! Ai terminat jocul!" // Mesaj final
        }
    }

    // --- Funcție pentru a reîncerca nivelul ---
    fun retryLevel() {
        Log.d(TAG, "retryLevel called.")
        playSound(context, R.raw.click)
        // Folosim trucul pentru a retrigera LaunchedEffect
        val currentIdx = currentLevelIndex
        currentLevelIndex = -1 // Invalid temporar
        scope.launch {
            delay(50)
            currentLevelIndex = currentIdx // Revine, retrigerează resetarea
        }
    }



 // --- Resetare la începutul nivelului ---
    LaunchedEffect(currentLevelIndex) {
        val levelData = gameLevels.getOrNull(currentLevelIndex)
        if (levelData != null) {
            Log.d(TAG, "Resetting state for Level ${levelData.levelId}: ${levelData.name}")

            // --- MODIFICAT/NOU: Aplică upgrade-ul "Mutări Extra" ---
            val extraMovesLevel = playerUpgrades["extra_moves"] ?: 0 // Obține nivelul upgrade-ului
            val bonusMoves = extraMovesLevel * 1 // Presupunem +1 mutare per nivel de upgrade. Poți schimba '1' la altă valoare.
            movesLeft = levelData.maxMoves + bonusMoves // Setează mutările totale
            Log.d(TAG, "movesLeft set to: ${levelData.maxMoves} (base) + $bonusMoves (bonus from Lvl $extraMovesLevel) = $movesLeft")
            // --- SFÂRȘIT MODIFICARE ---

            // Resetare restul stărilor
            board = generateValidInitialBoard()
            objectiveProgress = levelData.objectives.associateWith { 0 }
            inventory = emptyMap()
            // score = 0 // Comentat dacă ai decis să-l elimini mai târziu
            gameState = "Playing"
            feedbackMessage = "Nivel ${levelData.levelId}: ${levelData.name}\nObiectiv principal: [Primul Obiectiv Aici]\nMutări: $movesLeft" // Feedback inițial actualizat
            selectedTilePos = null
            tilesBeingMatched = emptySet()
            isProcessing = false
            swapAnimationFinished = true
            swappingTiles = null
            currentTileMovements = emptyList()
            // cookedMealsInventory = emptyMap() // Resetezi și mâncarea gătită? Decizie de design.
            // playerXP = 0 // Resetezi XP la fiecare nivel? Sau e global? Momentan e global.

        } else {
            Log.e(TAG, "Invalid level index or game finished: $currentLevelIndex")
            if (currentLevelIndex >= gameLevels.size) { // Verifică dacă am terminat toate nivelele
                feedbackMessage = "Felicitări! Ai terminat toate nivelele jocului!"
                gameState = "Finished"
            }
        }
    }


    // --- Efect pentru a rula animația de SWAP ---
    LaunchedEffect(swappingTiles) {
        val tilesToAnimate = swappingTiles // Perechea curentă care trebuie animată
        if (tilesToAnimate != null) {
            Log.d(TAG, "LaunchedEffect: Animating swap for $tilesToAnimate")
            val (pos1, pos2) = tilesToAnimate
            val xDiff = (pos2.col - pos1.col)
            val yDiff = (pos2.row - pos1.row)

            // --- Calcul tileSize în Pixeli (aproximativ) ---
            var tileSizePx = 0f
            // TODO: Găsește o metodă mai bună de a obține tileSize în pixeli aici
            with(density) {
                tileSizePx = 45.dp.toPx() // Ajustează 45.dp la o valoare realistă
            }
            Log.d(TAG, "Estimated tileSizePx for animation: $tileSizePx")

            // --- Animația Inițială (Dus) ---
            val job1 = scope.launch {
                tile1Offset.snapTo(IntOffset.Zero)
                tile1Offset.animateTo(
                    // Înmulțim diferența de grid cu dimensiunea în pixeli
                    targetValue = IntOffset(x = (xDiff * tileSizePx).toInt(), y = (yDiff * tileSizePx).toInt()),
                    animationSpec = tween(durationMillis = 250)
                )
            }
            val job2 = scope.launch {
                tile2Offset.snapTo(IntOffset.Zero)
                tile2Offset.animateTo(
                    // Înmulțim diferența de grid cu dimensiunea în pixeli
                    targetValue = IntOffset(x = (-xDiff * tileSizePx).toInt(), y = (-yDiff * tileSizePx).toInt()),
                    animationSpec = tween(durationMillis = 250)
                )
            }

            // Așteaptă finalul animației "dus"
            Log.d(TAG, "Waiting for initial swap animation to join...")
            job1.join()
            job2.join()
            Log.d(TAG, "Initial swap animation joined.")

            // ---  Verifică dacă swap-ul a fost valid ---
            val boardCheck = board.map { it.toMutableList() } // Verifică pe starea CURENTĂ
            val tempCheck = boardCheck[pos1.row][pos1.col]
            boardCheck[pos1.row][pos1.col] = boardCheck[pos2.row][pos2.col]
            boardCheck[pos2.row][pos2.col] = tempCheck
            val potentialMatches = findMatchesOnBoard(boardCheck)

            if (potentialMatches.isNotEmpty()) {
                // --- CAZ: SWAP VALID - Procesează potrivirile ---
                Log.d(TAG, "Swap valid. Performing logic.")
                tile1Offset.snapTo(IntOffset.Zero) // Resetează vizual
                tile2Offset.snapTo(IntOffset.Zero)
                performValidSwapAndProcess(pos1, pos2)

            } else {
                // --- CAZ: SWAP INVALID - Animație "Întors" ---
                Log.d(TAG, "Swap was INVALID. Animating back.")
                feedbackMessage = "Fără potrivire..." // Setează mesajul ACUM
                Log.d(TAG, "!!! SWAP INVALID - Initiating SHAKE BACK animation !!!")

                // Animație rapidă înapoi la poziția inițială (offset 0)
                val jobBack1 = scope.launch {
                    tile1Offset.animateTo(
                        targetValue = IntOffset.Zero, // Revine la 0 pixeli offset
                        animationSpec = tween(durationMillis = 150)
                    )
                }
                val jobBack2 = scope.launch {
                    tile2Offset.animateTo(
                        targetValue = IntOffset.Zero,
                        animationSpec = tween(durationMillis = 150)
                    )
                }
                // Așteaptă finalul animației "întors"
                Log.d(TAG, "Waiting for shake back animations to join...")
                jobBack1.join()
                jobBack2.join()
                Log.d(TAG, "Shake back animation joined.")

                // Verifică finalul nivelului (poate a fost ultima mutare)
                checkLevelEndCondition()
                Log.d(TAG,"Invalid swap post-animation check done.")
            }

            // --- Resetare finală indiferent de caz ---
            swappingTiles = null // Gata cu acest swap
            swapAnimationFinished = true // Permite următorul click/swap
            Log.d(TAG, "Swap animation state reset.")

        }
    }

    // === Decizia de Afișare ===
    if (showRecipeBookScreen) {
        RecipeBookScreen(
            recipes = availableRecipes,
            inventory = inventory, // Pasează starea
            canCookChecker = ::canCookRecipe,
            onCookRecipe = ::cookRecipe,
            onShowRecipeDetails = { recipe -> selectedRecipeToShow = recipe }, // Setează starea pt dialog
            onClose = { playSound(context, R.raw.click); showRecipeBookScreen = false } // Modifică starea de navigare
        )
    } else {
        GameScreen(
            // Date de afișat
            movesLeft = movesLeft,
            currentLevelData = currentLevelData,
            objectiveProgress = objectiveProgress,
            feedbackMessage = feedbackMessage,
            inventory = inventory,
            board = board,
            selectedTilePosition = selectedTilePos,
            tilesBeingMatched = tilesBeingMatched,
            isProcessing = isProcessing || !swapAnimationFinished,
            gameState = gameState,
            swappingTilesInfo = swappingTiles,
            tile1AnimatedOffset = tile1Offset.value,
            tile2AnimatedOffset = tile2Offset.value,
            playerXP = playerXP,
            availableRecipesCount = availableRecipes.size,
            playerMoney = playerMoney,
            currentLevelId = currentLevelData?.levelId ?: -1,
            // Callback-uri
            onTileClick = ::handleTileClick,
            onShowRecipeBook = { playSound(context, R.raw.click); showRecipeBookScreen = true },
            onShowShop = { playSound(context, R.raw.click); showShopDialog = true },
            onShowUpgrades = { playSound(context, R.raw.click); showUpgradesScreen = true },
            onRetryLevel = ::retryLevel,
            onNextLevel = ::goToNextLevel,
            tileMovements = currentTileMovements,
        )
    }

    // === Decizia de Afișare ===
    if (showUpgradesScreen) {
        UpgradesScreen(
            allPossibleUpgrades = availableUpgrades,
            currentOwnedUpgrades = playerUpgrades,
            currentPlayerMoney = playerMoney,
            onPurchaseUpgrade = ::purchaseUpgrade, // Pasează referința la funcția ta
            onClose = {
                playSound(context, R.raw.click)
                showUpgradesScreen = false
            }
        )
    }

    // --- Afișează dialogul PESTE orice ecran ---
    if (selectedRecipeToShow != null) {
        RecipeDetailDialog(
            recipe = selectedRecipeToShow!!,
            inventory = inventory,
            canCookChecker = ::canCookRecipe,
            onCook = ::cookRecipe, // Folosește funcția de gătit definită aici
            onDismiss = { playSound(context, R.raw.click); selectedRecipeToShow = null } // Închide dialogul
        )
    }

    // --- Afișare Dialog Shop ---
    if (showShopDialog) {
        ShopDialog(
            cookedMeals = cookedMealsInventory,
            recipesData = allPossibleRecipes, // Pasează lista completă pentru prețuri/nume
            onSellAll = {
                sellCookedMeals() // Apelează logica de vânzare
                showShopDialog = false // Închide dialogul după vânzare
            },
            onDismiss = { showShopDialog = false } // Închide dialogul la dismiss
        )
    }




    // --- Funcție pentru Cumpărarea/Îmbunătățirea unui Upgrade ---



}


// --- GameScreen Composable ---
@Composable
fun GameScreen(
    // Date de afișat
    movesLeft: Int,
    currentLevelData: LevelData?,
    objectiveProgress: Map<LevelObjective, Int>,
    feedbackMessage: String,
    inventory: Map<Int, Int>,
    board: List<List<Int>>,
    selectedTilePosition: TilePosition?,
    tilesBeingMatched: Set<TilePosition>,
    isProcessing: Boolean, // Indică dacă utilizatorul ar trebui blocat
    gameState: String,
    playerXP: Int,
    playerMoney: Int,
    availableRecipesCount: Int,
    swappingTilesInfo: Pair<TilePosition, TilePosition>?,
    tile1AnimatedOffset: IntOffset,
    tile2AnimatedOffset: IntOffset,
    // Callback-uri
    onTileClick: (row: Int, col: Int) -> Unit,
    onShowRecipeBook: () -> Unit,
    onRetryLevel: () -> Unit,
    onNextLevel: () -> Unit,
    currentLevelId: Int, //  Primește ID-ul nivelului curent
    onShowShop: () -> Unit, // Callback pentru shop
    onShowUpgrades: () -> Unit,
    tileMovements: List<TileMovementInfo>
) {
    val context = LocalContext.current // Obține context

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding() // Padding pentru barele sistemului
            .padding(16.dp), // Padding general
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // --- Afișare Stare Joc (Victorie/Înfrângere) ---
        if (gameState == "Won") {
            Button(onClick = {
                playSound(context, R.raw.click) // Sunet click UI !!!
                onNextLevel()
            }) { Text("Nivelul Următor") }
        }
        else if (gameState == "Lost") {
            Button(onClick = {
                playSound(context, R.raw.click) // Sunet click UI !!!
                onRetryLevel()
            }) { Text("Reîncearcă Nivelul") }
        }

        // --- Rând Superior: Scor, XP, Rețete ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Grup XP
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("XP:", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Text(playerXP.toString(), /* ... stil ... */) }

            // --- Grup Bani (Monedă) ---
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painterResource(id = R.drawable.coin),
                    contentDescription = "Bani",
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = playerMoney.toString(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                )
            }


            // Grup Rețete (cu iconiță)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onShowRecipeBook() } // Aplică clickable AICI
            ) {
                Image(
                    painter = painterResource(id = R.drawable.carte),
                    contentDescription = "Rețete (${availableRecipesCount})", // Descriere mai bună
                    modifier = Modifier.size(40.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = availableRecipesCount.toString(),
                    style = MaterialTheme.typography.bodyLarge, // Ajustează stilul dacă vrei
                    fontWeight = FontWeight.Bold
                )
            }

            // --- Grup Shop (Iconiță Clickabilă) ---
            IconButton(onClick = {
                playSound(context, R.raw.click)
                onShowShop() // Deschide dialogul Shop
            }) {
                Image(
                    painter = painterResource(id = R.drawable.market),
                    contentDescription = "Magazin (Vinde Produse)",
                    modifier = Modifier.size(32.dp)
                )
            }

            // --- Grup Upgrade-uri (Iconiță Clickabilă) ---
            IconButton(onClick = {
                playSound(context, R.raw.click)
                onShowUpgrades() // Apelează noul callback
            }) {
                Image(
                    painter = painterResource(id = R.drawable.upgrade),
                    contentDescription = "Îmbunătățiri",
                    modifier = Modifier.size(30.dp)
                )
            }


        }
        Spacer(modifier = Modifier.height(8.dp))


        // --- Rând Info Nivel: Mutări și Obiective Compacte ---
        if (currentLevelData != null && gameState == "Playing") {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Nume Nivel
                Text(
                    text = "Nivel ${currentLevelData.levelId}: ${currentLevelData.name}",
                    style = MaterialTheme.typography.titleSmall, // Font mai mic
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))

                // Rând pentru Mutări și Obiective Principale
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly, // Sau SpaceBetween
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mutări Rămase
                    Text(
                        text = "Mutări: $movesLeft",
                        style = MaterialTheme.typography.bodyMedium, // Font normal
                        fontWeight = FontWeight.Bold,
                        color = if (movesLeft <= 5 && movesLeft > 0) Color.Red else MaterialTheme.colorScheme.onSurface
                    )

                    // --- Afișare Obiective  ---
                    // Afișăm doar 1-2 obiective principale sau folosim iconițe
                    // Exemplu: Afișează doar PRIMUL obiectiv neîndeplinit
                    val firstUnmetObjective = currentLevelData.objectives.firstOrNull { (objectiveProgress[it] ?: 0) < it.targetQuantity }
                    if (firstUnmetObjective != null) {
                        val progress = objectiveProgress[firstUnmetObjective] ?: 0
                        val objectiveText = formatObjective(firstUnmetObjective, progress) // Folosim o funcție helper
                        Text(
                            text = "🎯 $objectiveText", // Folosim emoji sau iconiță
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 13.sp // Font puțin mai mic
                        )
                    } else if (currentLevelData.objectives.isNotEmpty()) {
                        // Toate obiectivele sunt îndeplinite (dar jocul nu s-a terminat încă?)
                        Text("Obiectiv realizat! ✅", fontSize = 13.sp, color = Color.Gray)
                    }
                    // TODO: Poți adăuga un mic indicator dacă sunt MAI MULTE obiective
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // --- Mesaj Feedback  ---
        Text(text = feedbackMessage, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().heightIn(min = 18.dp), fontSize = 15.sp)
        Spacer(modifier = Modifier.height(8.dp))

        // --- Afișaj Inventar  ---
        Text("Inventar Ingrediente:", style = MaterialTheme.typography.labelLarge) // Sau alt stil
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp), // Padding redus poate?
            horizontalArrangement = if (inventory.isEmpty()) Arrangement.Center else Arrangement.Start // Centrează mesajul dacă e gol
        ) {
            if (inventory.isEmpty()) {
                Text("Rucsacul e gol! Joacă pentru ingrediente.", fontSize = 12.sp, color = Color.Gray)
            } else {
                // Folosim LazyRow dacă pot fi multe ingrediente și vrem scroll orizontal
                // Sau un FlowRow (experimental) pentru a se înfășura pe mai multe rânduri.
                // Momentan, un Row simplu care poate depăși ecranul dacă sunt prea multe.
                inventory.entries.sortedBy { it.key }.forEach { (ingredientId, quantity) ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 6.dp) // Spațiu între itemele din inventar
                    ) {
                        val drawableResId = tileDrawables[ingredientId]
                        if (drawableResId != null) {
                            Image(
                                painter = painterResource(id = drawableResId),
                                contentDescription = getIngredientName(ingredientId),
                                modifier = Modifier.size(28.dp) // Mărime iconiță inventar
                            )
                        } else {
                            Box(Modifier.size(28.dp).background(tileColors[ingredientId] ?: Color.Gray, CircleShape))
                        }
                        Text(
                            text = quantity.toString(),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        // Opcional, numele sub cantitate
                        // Text(getIngredientName(ingredientId), fontSize = 9.sp)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))

        // Afișează butonul pentru piata
        val shouldShowShopButton = currentLevelId == 1
        // Sau poți folosi gameState == "Won" și o condiție pe levelId
        if (shouldShowShopButton && gameState == "Playing") { // Sau la final de nivel? Momentan în timpul jocului
            Spacer(modifier = Modifier.height(10.dp))

        }






        // --- Tabla de Joc ---
        // Folosim weight pentru a împinge tabla în jos, DAR nu prea mult
        Box(modifier = Modifier.weight(0.8f)) { // Încearcă diferite valori < 1f
            if (gameState == "Playing" || gameState == "Won" || gameState == "Lost") {
                GameBoard(
                    onTileClick = { row, col ->
                        if (gameState == "Playing" && !isProcessing)
                        onTileClick(row, col)
                    },
                    board = board,
                    selectedTilePosition = selectedTilePosition,
                    tilesBeingMatched = tilesBeingMatched,
                    swappingTilesInfo = swappingTilesInfo,
                    tile1AnimatedOffset = tile1AnimatedOffset,
                    tile2AnimatedOffset = tile2AnimatedOffset,
                    tileMovements = tileMovements
                )
            } else { /* Spacer sau mesaj "Joc Terminat" */ }
        }

        // Spacer final mic
        Spacer(modifier = Modifier.height(8.dp))
    }
}


// --- Funcție Helper pentru Formatare Obiectiv (la nivel de fișier sau în App) ---
fun formatObjective(objective: LevelObjective, progress: Int): String {
    val targetQuantity = objective.targetQuantity

    // Ajustăm progresul curent pentru a nu depăși ținta la afișare
    val displayProgress = when (objective.type) {
        else -> progress.coerceAtMost(targetQuantity) // Pentru COLLECT_INGREDIENTS, COOK_RECIPES
    }

    return when (objective.type) {
        ObjectiveType.COLLECT_INGREDIENTS -> {
            // Asigură-te că getIngredientName gestionează corect ID-ul din objective.targetId
            val ingredientName = getIngredientName(objective.targetId)
            "Colectează $ingredientName: $displayProgress / $targetQuantity"
        }
        ObjectiveType.COOK_RECIPES -> {
            // Găsește numele rețetei după ID-ul stocat în objective.targetId
            val recipeName = allPossibleRecipes.find { it.id == objective.targetId }?.name ?: "Rețetă ID ${objective.targetId}"
            "Gătește $recipeName: $displayProgress / $targetQuantity"
        }
        // Adaugă aici alte cazuri dacă vei avea noi ObjectiveType
    }
}


// --- RecipeBookScreen Composable (Simplificat - primește totul ca parametri) ---
@Composable
fun RecipeBookScreen(
    recipes: List<Recipe>,
    inventory: Map<Int, Int>,
    canCookChecker: (Recipe) -> Boolean,
    onCookRecipe: (Recipe) -> Unit, // Pasează funcția de gătit reală
    onShowRecipeDetails: (Recipe) -> Unit, // Pentru a deschide dialogul
    onClose: () -> Unit
) {
    // NU mai are nevoie de stare locală pentru dialog
    // var recipeForDialog by remember { mutableStateOf<Recipe?>(null) }
    val context = LocalContext.current
    Column(  modifier = Modifier
        .fillMaxSize()
        .systemBarsPadding()
        .padding(16.dp) ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                playSound(context, R.raw.click) // Sunet UI
                onClose()
            }) { Icon(Icons.Default.ArrowBack, contentDescription = "Înapoi") }
            Spacer(modifier = Modifier.width(8.dp))
            Text("Cartea Mea de Bucate", style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(recipes, key = { it.id }) { recipe ->
                val canCook = remember(inventory, recipe) { canCookChecker(recipe) }
                Column(
                    modifier = Modifier.clickable {
                        playSound(context, R.raw.click) // Sunet UI
                        onShowRecipeDetails(recipe)
                    }
                        .fillMaxWidth()
                        .clickable { onShowRecipeDetails(recipe) } // Apelează callback-ul pentru dialog
                        .padding(vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) { /* ... Nume + Indicator Gata/Lipsă ... */ }
                    Text(recipe.name, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                Divider()
            }
            if (recipes.isEmpty()){ /* ... item listă goală ... */ }
        }
    }
}



// --- Composable pentru Dialogul Detalii Rețetă ---
@Composable
fun RecipeDetailDialog(
    recipe: Recipe, // Rețeta de afișat
    inventory: Map<Int, Int>, // Inventarul curent pentru verificare
    canCookChecker: (Recipe) -> Boolean, // Funcția care verifică dacă se poate găti
    onCook: (Recipe) -> Unit, // Funcția de apelat la apăsarea "Gătește"
    onDismiss: () -> Unit // Funcția de apelat la închiderea dialogului
) {
    val canCookCurrentRecipe = remember(inventory, recipe) { canCookChecker(recipe) } // Verifică dacă se poate găti
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss, // Apelează funcția primită
        title = { Text(text = recipe.name) },
        text = {
            Column {
                Text(recipe.description, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(12.dp))
                Text("Ingrediente Necesare:", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                recipe.ingredientsNeeded.forEach { (ingredientId, quantityNeeded) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        val drawableResId = tileDrawables[ingredientId]
                        if (drawableResId != null) { /* ... Image ... */ }
                        else { /* ... Box fallback ... */ }
                        Spacer(modifier = Modifier.width(8.dp))
                        val quantityOwned = inventory.getOrDefault(ingredientId, 0)
                        Text("${getIngredientName(ingredientId)}: $quantityOwned / $quantityNeeded")
                        if (quantityOwned < quantityNeeded) {
                            Text(" (Lipsă!)", color = Color.Red, fontSize = 10.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(
                    onClick = {
                        onCook(recipe) // Apelează funcția primită
                    },
                    enabled = canCookCurrentRecipe
                ) {
                    Text("Gătește")
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDismiss) { // Apelează funcția primită
                    Text("Înapoi")
                }
            }
        }
    )
}

// --- GameTile Composable ---
@Composable
fun GameTile(
    modifier: Modifier = Modifier, // Modifier extern (poziție, etc.)
    type: Int,
    size: Dp,
    isSelected: Boolean,
    isDisappearing: Boolean, // Doar pentru dispariție
    animatedOffset: IntOffset, // Doar pentru SWAP
    onClick: () -> Unit
) {
    // Stări DOAR pentru Dispariție
    val disappearingScale = remember { Animatable(1f) }
    val disappearingAlpha = remember { Animatable(1f) }

    // LaunchedEffect DOAR pentru Dispariție
    LaunchedEffect(isDisappearing) {
        if (isDisappearing) {
            launch { disappearingScale.animateTo(0.3f, tween(300)) }
            launch { disappearingAlpha.animateTo(0f, tween(300)) }
        } else {
            // Resetare dispariție (dacă nu dispare)
            if (disappearingScale.value != 1f) disappearingScale.snapTo(1f)
            if (disappearingAlpha.value != 1f) disappearingAlpha.snapTo(1f)
        }
    }

    // Modificator Selecție
    val selectionModifier = if (isSelected) {
        Modifier // Începe lanțul pentru stare selectată
            .border(
                width = 2.dp, // Sau 3.dp
                color = Color.Yellow,
                shape = MaterialTheme.shapes.small
            )
            .scale(1.05f) // Mărește ușor
    } else { Modifier }

    val drawableResId = tileDrawables[type]

    // Combinăm Modifiers
    val combinedModifier = modifier // Modifier extern
        .offset { animatedOffset } // Offset Swap
        .graphicsLayer { // Doar Scale/Alpha pentru dispariție
            scaleX = disappearingScale.value
            scaleY = disappearingScale.value
            this.alpha = disappearingAlpha.value
        }
        .size(size)
        .padding(1.dp)
        .then(selectionModifier)
        .background(
            color = tileColors[type]?.copy(alpha = 0.4f) ?: Color.Gray.copy(alpha = 0.4f),
            shape = MaterialTheme.shapes.small
        )
        .clickable(onClick = onClick)

    Box(
        modifier = combinedModifier,
        contentAlignment = Alignment.Center
    ) {
        if (drawableResId != null) {
            Image(
                painter = painterResource(
                    id = drawableResId),
                contentDescription = getIngredientName(type),
                modifier = Modifier.fillMaxSize(0.8f)
            )
        }
    }
}


// --- GameBoard Composable ---
@Composable
fun GameBoard(
    board: List<List<Int>>, // Starea logică (poate avea EMPTY_TILE în timpul anim.)
    selectedTilePosition: TilePosition?,
    tilesBeingMatched: Set<TilePosition>, // Piesele care dispar ACUM
    swappingTilesInfo: Pair<TilePosition, TilePosition>?,
    tile1AnimatedOffset: IntOffset,
    tile2AnimatedOffset: IntOffset,
    tileMovements: List<TileMovementInfo>, // Mișcările de cădere/apariție ACTIVE
    onTileClick: (row: Int, col: Int) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(Color(0xFFA0A0A0))
            .padding(4.dp)
    ) {
        val tileSize = maxWidth / COLS
        val tileSizePx = with(LocalDensity.current) { tileSize.toPx() }
        Log.d(TAG, "GameBoard Recomposing. Movements: ${tileMovements.size}, Matched: ${tilesBeingMatched.size}")

        // Container principal pentru a suprapune piesele statice și cele animate
        Box(modifier = Modifier.fillMaxSize()) {

            // --- 1. Desenăm Grid-ul de Bază (Static + Cele care Dispar) ---
            Column { // Folosim Column/Row pentru layout-ul de bază
                board.forEachIndexed { rowIndex, rowData ->
                    Row {
                        rowData.forEachIndexed { colIndex, logicalTileType ->
                            val currentPos = TilePosition(rowIndex, colIndex)
                            // Verificăm dacă o piesă se mișcă SPRE această poziție
                            val isTileMovingToThisSpot = tileMovements.any { it.finalRow == rowIndex && it.col == colIndex }
                            // Verificăm dacă ACEASTĂ piesă dispare
                            val isDisappearing = tilesBeingMatched.contains(currentPos)

                            // --- Desenăm ceva în această celulă? ---
                            // Desenăm dacă:
                            // - Piesa logică NU e goală ȘI NU vine alta peste ea
                            // SAU dacă piesa de aici dispare ACUM (pentru a rula anim. dispariție)
                            if ((logicalTileType != EMPTY_TILE && !isTileMovingToThisSpot) || isDisappearing) {
                                val isSelected = currentPos == selectedTilePosition
                                val swapOffset = when (currentPos) {
                                    swappingTilesInfo?.first -> tile1AnimatedOffset
                                    swappingTilesInfo?.second -> tile2AnimatedOffset
                                    else -> IntOffset.Zero
                                }

                                GameTile(
                                    // Fără modifier de poziție extern, e în Column/Row
                                    // Tipul este cel logic din board, pt că GameTile va dispărea dacă isDisappearing=true
                                    type = logicalTileType,
                                    size = tileSize,
                                    isSelected = isSelected,
                                    isDisappearing = isDisappearing, // Pasează corect starea
                                    animatedOffset = swapOffset, // Doar offset-ul de SWAP
                                    // NU pasăm movementInfo sau tileSizePx la GameTile simplificat
                                    onClick = { onTileClick(rowIndex, colIndex) }
                                )
                            } else {
                                // Altfel, lăsăm un spațiu gol în grid
                                Spacer(modifier = Modifier.size(tileSize))
                            }
                        }
                    }
                }
            } // --- Sfârșit Grid de Bază ---


            // --- 2. Desenăm piesele ÎN MIȘCARE (Cădere/Apariție) PESTE ---
            tileMovements.forEach { movement ->
                // Starea inițială a animației
                val initialOffsetY = if (movement.isNew) -tileSizePx * (movement.finalRow.toFloat() + 3f) else -(movement.fallDistance * tileSizePx)
                val initialAlpha = if (movement.isNew) 0f else 1f

                // Animatable PENTRU ACEASTĂ INSTANȚĂ DE MIȘCARE
                // Folosim movement ca cheie pentru remember, ca să fie unic pt fiecare mișcare
                val translationY = remember(movement) { Animatable(initialOffsetY) }
                val alpha = remember(movement) { Animatable(initialAlpha) }

                // Rulează animația specifică acestei piese
                LaunchedEffect(movement) {
                    val delayMillis = (movement.col * 30L + movement.fallDistance * 10L).toInt()
                    Log.d(TAG, "Animating Fall: T=${movement.tileType} to (${movement.finalRow},${movement.col}) from Y=${initialOffsetY.toInt()} with delay=$delayMillis")
                    launch { translationY.animateTo(0f, tween(350, delayMillis)) } // Durata căderii
                    if (movement.isNew) { launch { alpha.animateTo(1f, tween(350, delayMillis)) } }
                    else { alpha.snapTo(1f) }
                }

                // Calculăm poziția X și Y finală în Box-ul mare
                val finalPosX = movement.col * tileSizePx
                val finalPosY = movement.finalRow * tileSizePx

                // Calculăm offset-ul de la SWAP (dacă piesa AJUNGE într-o poziție de swap)
                val finalPos = TilePosition(movement.finalRow, movement.col)
                val swapOffset = when (finalPos) {
                    swappingTilesInfo?.first -> tile1AnimatedOffset
                    swappingTilesInfo?.second -> tile2AnimatedOffset
                    else -> IntOffset.Zero
                }

                // Desenăm piesa în mișcare într-un Box poziționat manual
                Box(
                    modifier = Modifier
                        .graphicsLayer { // Aplicăm alpha AICI
                            this.alpha = alpha.value
                        }
                        .offset { // Aplicăm poziția finală + căderea animată + swap AICI
                            IntOffset(
                                x = finalPosX.toInt() + swapOffset.x,
                                y = finalPosY.toInt() + translationY.value.toInt() + swapOffset.y
                            )
                        }
                ) {
                    GameTile(
                        // Fără modifier explicit de poziție aici
                        type = movement.tileType, // Tipul piesei care cade/apare
                        size = tileSize,
                        isSelected = (finalPos == selectedTilePosition), // Verificăm selecția pe poziția finală
                        isDisappearing = false, // O piesă care cade/apare nu dispare
                        animatedOffset = IntOffset.Zero, // Swap-ul e deja inclus în offset-ul Box-ului
                        onClick = { onTileClick(movement.finalRow, movement.col) } // Click pe poziția finală
                    )
                }
            } // --- Sfârșit forEach tileMovements ---
        } // --- Sfârșit Box container principal ---
    }
}



// --- Composable pentru Dialogul Shop ---
@Composable
fun ShopDialog(
    cookedMeals: Map<Int, Int>, // Inventarul de mâncare gătită <RecipeId, Quantity>
    recipesData: List<Recipe>, // Avem nevoie de datele rețetelor pentru nume și preț
    onSellAll: () -> Unit, // Funcția de apelat la apăsarea "Vinde Tot"
    onDismiss: () -> Unit // Funcția de apelat la închiderea dialogului
) {
    // Calculează valoarea totală a mărfii (opțional, pentru afișare)
    var totalValue = 0
    cookedMeals.forEach { (recipeId, quantity) ->
        val recipe = recipesData.find { it.id == recipeId }
        if (recipe != null) {
            totalValue += recipe.sellingPrice * quantity
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Piața Locală (Magazin)") },
        text = {
            Column {
                if (cookedMeals.isEmpty()) {
                    Text("Nu ai pregătit nimic de vânzare încă!")
                } else {
                    Text("Produse gata de vânzare:")
                    Spacer(modifier = Modifier.height(8.dp))
                    // Listează produsele gătite
                    LazyColumn { // Folosim LazyColumn dacă lista poate fi lungă
                        items(cookedMeals.entries.toList(), key = { it.key }) { (recipeId, quantity) ->
                            val recipe = recipesData.find { it.id == recipeId }
                            if (recipe != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // Iconiță mică a ingredientului principal (sau a rețetei?) - Simplificare: folosim primul ingredient
                                        val firstIngredientId = recipe.ingredientsNeeded.keys.firstOrNull()
                                        val drawableResId = if(firstIngredientId != null) tileDrawables[firstIngredientId] else null
                                        if (drawableResId != null) {
                                            Image(painterResource(id = drawableResId), contentDescription = null, modifier = Modifier.size(24.dp))
                                        } else {
                                            Box(Modifier.size(24.dp)) // Placeholder
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("${recipe.name} x $quantity")
                                    }
                                    Text("+${recipe.sellingPrice * quantity} Bani", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Valoare Totală: $totalValue Bani", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                }
            }
        },
        confirmButton = {
            Row {
                Button(
                    onClick = onSellAll,
                    enabled = cookedMeals.isNotEmpty() // Activează doar dacă e ceva de vândut
                ) {
                    Text("Vinde Tot")
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDismiss) {
                    Text("Închide")
                }
            }
        }
    )
}

// --- Preview-uri  ---
@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    Match3PuzzleGameTheme {
        Match3GameApp()
    }
}

