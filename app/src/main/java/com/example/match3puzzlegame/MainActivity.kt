// ATENȚIE: Asigură-te că acest nume de pachet se potrivește cu structura proiectului tău!
package com.example.match3puzzlegame
import kotlinx.coroutines.delay // Pentru pauze
import kotlinx.coroutines.launch // Pentru a porni corutina
import android.os.Bundle
import android.util.Log // Pentru depanare
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.* // Include remember, mutableStateOf, Composable etc.
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale // Import pentru scalare
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.match3puzzlegame.ui.theme.Match3PuzzleGameTheme
import kotlin.math.abs
import androidx.compose.animation.core.Animatable // Pentru animație detaliată
import androidx.compose.animation.core.tween // Specifică durata animației
import androidx.compose.ui.graphics.graphicsLayer // Pentru a aplica scale și alpha

// --- Constante ---
const val ROWS = 8
const val COLS = 8
const val META_COST = 100
const val EMPTY_TILE = 0
const val TILE_TYPE_1 = 1
const val TILE_TYPE_2 = 2
const val TILE_TYPE_3 = 3
const val TILE_TYPE_4 = 4
const val TILE_TYPE_5 = 5
val TILE_TYPES = listOf(TILE_TYPE_1, TILE_TYPE_2, TILE_TYPE_3, TILE_TYPE_4, TILE_TYPE_5)

// Definirea explicită a tipului Map-ului
val tileColors: Map<Int, Color> = mapOf(
    EMPTY_TILE to Color.Transparent,
    TILE_TYPE_1 to Color.Red.copy(alpha = 0.8f),
    TILE_TYPE_2 to Color(0xFFFFA500), // Orange
    TILE_TYPE_3 to Color.Blue.copy(alpha = 0.8f),
    TILE_TYPE_4 to Color.Green.copy(alpha = 0.8f),
    TILE_TYPE_5 to Color.Magenta.copy(alpha = 0.8f)
)

// --- Clasa pentru Poziție ---
data class TilePosition(val row: Int, val col: Int)

// --- TAG pentru Logcat ---
private const val TAG = "Match3Game"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Match3PuzzleGameTheme {
                GameScreen()
            }
        }
    }
}




@Composable
fun GameScreen() {
    var score by remember { mutableStateOf(0) }

    var feedbackMessage by remember { mutableStateOf("") }

    var selectedTilePos by remember { mutableStateOf<TilePosition?>(null) }

    var tilesBeingMatched by remember { mutableStateOf<Set<TilePosition>>(emptySet()) }

    var isProcessing by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    // Optimizare stare derivată pentru butonul meta
    val isMetaButtonEnabled = remember(score) { score >= META_COST }
    val metaButtonText = remember(score) {
        if (isMetaButtonEnabled) {
            "Renovează (Costă $META_COST Stele)"
        } else {
            val needed = META_COST - score
            "Adună $needed Stele"
        }
    }

    // --- Funcție Helper pentru Adiacență --- *NOU*
    fun areAdjacent(pos1: TilePosition, pos2: TilePosition): Boolean {
        val rowDiff = abs(pos1.row - pos2.row)
        val colDiff = abs(pos1.col - pos2.col)
        return (rowDiff == 1 && colDiff == 0) || (rowDiff == 0 && colDiff == 1)
    }




    fun findMatchesOnBoard(targetBoard: List<List<Int>>): Set<TilePosition> {
        val matches = mutableSetOf<TilePosition>()
        val currentBoard = targetBoard
        for (r in 0 until ROWS) {
            var currentStreak = 1
            var currentType = -1 // Tip invalid inițial
            for (c in 0 until COLS) {
                val tileType = targetBoard.getOrNull(r)?.getOrNull(c) ?: EMPTY_TILE // Folosește targetBoard
                if (tileType != EMPTY_TILE && tileType == currentType) {
                    currentStreak++
                } else {
                    if (currentStreak >= 3) {
                        for (i in 1..currentStreak) { matches.add(TilePosition(r, c - i)) }
                    }
                    currentType = tileType
                    currentStreak = if (tileType != EMPTY_TILE) 1 else 0
                }
            }
            if (currentStreak >= 3) {
                for (i in 1..currentStreak) { matches.add(TilePosition(r, COLS - i)) }
            }
        }
        // Verificare Verticală
        for (c in 0 until COLS) {
            var currentStreak = 1
            var currentType = -1
            for (r in 0 until ROWS) {
                val tileType = targetBoard.getOrNull(r)?.getOrNull(c) ?: EMPTY_TILE // Folosește targetBoard
                if (tileType != EMPTY_TILE && tileType == currentType) {
                    currentStreak++
                } else {
                    if (currentStreak >= 3) {
                        for (i in 1..currentStreak) { matches.add(TilePosition(r - i, c)) }
                    }
                    currentType = tileType
                    currentStreak = if (tileType != EMPTY_TILE) 1 else 0
                }
            }
            if (currentStreak >= 3) {
                for (i in 1..currentStreak) { matches.add(TilePosition(ROWS - i, c)) }
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
        Log.w(TAG, "Could not generate a match-free initial board after 100 attempts. Using last generated board.")
        // Să returnăm totuși o tablă goală în acest caz extrem pentru a fi clar
        return List(ROWS) { MutableList(COLS) { EMPTY_TILE } } // Sau returnează ultima `candidateBoard`
    }

    var board by remember {
        Log.d(TAG, "Initializing board state by calling generateValidInitialBoard()")
        mutableStateOf(generateValidInitialBoard()) // <-- Linia NOUĂ
    }


    fun applyGravityToBoard(targetBoard: List<MutableList<Int>>): List<MutableList<Int>> {
        Log.d(TAG, "Applying gravity logic...")
        val newBoard = targetBoard.map { it.toMutableList() } // Copie mutabilă
        for (c in 0 until COLS) {
            val column = mutableListOf<Int>()
            // Adună toate piesele non-goale din coloană
            for (r in 0 until ROWS) {
                if (newBoard[r][c] != EMPTY_TILE) { // Folosește newBoard
                    column.add(newBoard[r][c])
                }
            }
            val emptyToAdd = ROWS - column.size
            for (r in 0 until ROWS) {
                newBoard[r][c] = if (r < emptyToAdd) EMPTY_TILE else column[r - emptyToAdd] // Modifică newBoard
            }
        }
        Log.d(TAG, "Gravity logic finished.")
        return newBoard // Returnează tipul corect
    }




    fun fillEmptyTilesOnBoard(targetBoard: List<MutableList<Int>>): List<MutableList<Int>> {
        Log.d(TAG, "Filling empty tiles logic...")
        val newBoard = targetBoard.map { it.toMutableList() }
        var filledAny = false
        for (r in 0 until ROWS) {
            for (c in 0 until COLS) {
                if (newBoard[r][c] == EMPTY_TILE) { // Verifică în copie
                    newBoard[r][c] = TILE_TYPES.random() // Modifică în copie
                    filledAny = true
                }
            }
        }
        Log.d(TAG, "Fill logic finished. Filled any: $filledAny")
        // Returnăm copia modificată (sau originalul dacă nu s-a umplut nimic, deși copia e mai sigură)
        return newBoard
    }



    suspend fun processMatchesAndCascades() {
        var currentBoard = board // Începe cu starea curentă
        var totalPointsThisTurn = 0
        var cascadeCount = 0

        while (true) { // Bucla cascadei
            val matches = findMatchesOnBoard(currentBoard) // Găsește potriviri pe tabla curentă

            if (matches.isEmpty()) {
                Log.d(TAG, "No more matches found, ending cascade loop.")
                if (cascadeCount > 0) { // Afișează scorul total doar dacă a fost cel puțin o potrivire
                    feedbackMessage = "Total: +$totalPointsThisTurn stele!"
                }
                break // Ieși din bucla while dacă nu mai sunt potriviri
            }
            tilesBeingMatched = matches
            cascadeCount++
            Log.d(TAG, "Cascade $cascadeCount: Found ${matches.size} matched tiles.")

            // --- 1. Procesează potrivirile (calcul scor, pregătește golirea) ---
            val pointsEarned = matches.size * 10 * cascadeCount // Bonus simplu pentru cascadă
            totalPointsThisTurn += pointsEarned
            score += pointsEarned // Actualizează scorul imediat

            feedbackMessage = if (cascadeCount > 1) "Cascadă $cascadeCount! +$pointsEarned" else "Potrivire! +$pointsEarned"

            val boardWithEmptyTiles = currentBoard.map { it.toMutableList() }
            matches.forEach { pos ->
                if (pos.row in 0 until ROWS && pos.col in 0 until COLS) {
                    boardWithEmptyTiles[pos.row][pos.col] = EMPTY_TILE
                }
            }

            // --- 2. Animație dispariție & Actualizare UI ---
            delay(400L) // Așteaptă vizual dispariția (timp similar cu animația CSS)

            val boardAfterMatch = currentBoard.map { it.toMutableList() }
            matches.forEach { pos ->
                if (pos.row in 0 until ROWS && pos.col in 0 until COLS) {
                    boardAfterMatch[pos.row][pos.col] = EMPTY_TILE
                }
            }
            tilesBeingMatched = emptySet()
            board = boardAfterMatch // Actualizează starea principală PENTRU a arăta spațiile goale
            currentBoard = boardAfterMatch // Continuăm procesarea de la această stare



            // --- 3. Aplică Gravitația ---
            val boardAfterGravity = applyGravityToBoard(currentBoard) // Funcție nouă care returnează tabla modificată

            delay(300L)
            board = boardAfterGravity
            currentBoard = boardAfterGravity



            // ---  Umple spațiile goale ---
            val boardAfterFill = fillEmptyTilesOnBoard(currentBoard) // Funcție nouă care returnează tabla modificată

            delay(300L)
            board = boardAfterFill
            currentBoard = boardAfterFill

        }
    }



        // --- Funcție Helper pentru Swap --- *NOU*
    fun swapTiles(pos1: TilePosition, pos2: TilePosition) {
        if (isProcessing) return // Verificare suplimentară

        Log.d(TAG, "Attempting swap between $pos1 and $pos2")

        // 1. Creează noua tablă cu piesele inversate
        val boardAfterSwap = board.map { it.toMutableList() }
        val temp = boardAfterSwap[pos1.row][pos1.col]
        boardAfterSwap[pos1.row][pos1.col] = boardAfterSwap[pos2.row][pos2.col]
        boardAfterSwap[pos2.row][pos2.col] = temp

        // 2. Verifică *potențialele* potriviri DUPĂ swap (fără a modifica starea încă)
        val potentialMatches = findMatchesOnBoard(boardAfterSwap) // Folosim o funcție ce primește tabla

        if (potentialMatches.isNotEmpty()) {
            // --- Swap valid - pornește procesarea ---
            Log.d(TAG, "Swap valid, starting processing coroutine")
            feedbackMessage = "" // Resetează feedback-ul
            selectedTilePos = null // Deselectează vizual
            isProcessing = true // Blochează input-ul

            // Actualizează starea pentru a ARĂTA swap-ul
            board = boardAfterSwap

            // Lansează corutina pentru procesarea cascadei
            scope.launch {
                processMatchesAndCascades() // Rulează ciclul complet
                isProcessing = false // Deblochează input-ul la sfârșit
                Log.d(TAG, "Processing finished.")
            }
        } else {
            // --- Swap invalid - nu face nimic vizual pe termen lung ---
            // (Am putea adăuga o animație scurtă de "shake" aici)
            Log.d(TAG, "Swap invalid, no matches formed.")
            feedbackMessage = "Mutare invalidă!"
            selectedTilePos = null // Deselectează oricum
        }
    }



    // --- Structura UI ---
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- Secțiunea Info Joc (rămâne la fel) ---
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Stele:", fontSize = 18.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = score.toString(),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE91E63)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        // --- Buton Meta (rămâne la fel) ---
        Button(
            onClick = {
                if (isMetaButtonEnabled) {
                    score -= META_COST
                    feedbackMessage = "Ai cheltuit $META_COST stele! 🎉"
                }
            },
            enabled = isMetaButtonEnabled
        ) {
            Text(text = metaButtonText)
        }
        Spacer(modifier = Modifier.height(8.dp))

        // --- Mesaj Feedback (rămâne la fel) ---
        Text(
            text = feedbackMessage,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = if (feedbackMessage.startsWith("Ai cheltuit")) Color.Magenta else Color.Green,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().heightIn(min = 20.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))


        // --- Tabla de Joc ---
        GameBoard(
            board = board,
            selectedTilePosition = selectedTilePos,
            tilesBeingMatched = tilesBeingMatched,
            onTileClick = { row, col ->
                if (isProcessing) { // *ADAUGAT*
                    Log.d(TAG, "Click ignorat - procesare în curs")
                    return@GameBoard
                }

                val clickedPos = TilePosition(row, col)
                Log.d(TAG, "onTileClick: ($row, $col)")

                val currentSelection = selectedTilePos // Copie locală

                if (currentSelection == null) {
                    // Prima Selecție
                    selectedTilePos = clickedPos
                    feedbackMessage = "Selectat: (${clickedPos.row}, ${clickedPos.col})"
                    Log.d(TAG, "Prima selecție: $clickedPos")
                } else {
                    // A Doua Selecție
                    if (clickedPos == currentSelection) {
                        // Click pe aceeași piesă: Deselectare
                        selectedTilePos = null
                        feedbackMessage = "Deselectat"
                        Log.d(TAG, "Deselectare")
                    } else if (areAdjacent(currentSelection, clickedPos)) {
                        // Click pe piesă adiacentă: Swap
                        swapTiles(currentSelection, clickedPos)
                        selectedTilePos = null // Deselectează după swap
                        Log.d(TAG, "Swap între $currentSelection și $clickedPos")
                        // Mesajul de feedback e setat în swapTiles
                    } else {
                        // Click pe piesă neadiacentă: Selectează noua piesă
                        selectedTilePos = clickedPos
                        feedbackMessage = "Selectat: (${clickedPos.row}, ${clickedPos.col})"
                        Log.d(TAG, "Selectare nouă (neadiacentă): $clickedPos")
                    }
                }
            }
        )
        Log.d(TAG, "GameBoard composition finished")
    }
}



@Composable
fun GameBoard(
    board: List<List<Int>>,
    selectedTilePosition: TilePosition?,
    tilesBeingMatched: Set<TilePosition>,
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
        Column {
            board.forEachIndexed { rowIndex, rowData ->
                Row {
                    rowData.forEachIndexed { colIndex, tileType ->
                        val currentPos = TilePosition(rowIndex, colIndex)
                        // Verifică dacă piesa curentă este cea selectată *MODIFICAT*
                        val isSelected = currentPos == selectedTilePosition
                        val isDisappearing = tilesBeingMatched.contains(currentPos)

                        if (tileType != EMPTY_TILE) { // Desenăm doar piese non-goale
                            GameTile(
                                type = tileType,
                                size = tileSize,
                                isSelected = isSelected,
                                isDisappearing = isDisappearing, // Pasează starea nouă
                                onClick = { onTileClick(rowIndex, colIndex) }
                            )
                        } else {
                            // Spațiu gol, nu desenăm nimic (sau un placeholder transparent)
                            Spacer(modifier = Modifier.size(tileSize))
                        }
                    }
                }
            }
        }
    }
}



@Composable
fun GameTile(
    type: Int,
    size: Dp,
    isSelected: Boolean,
    isDisappearing: Boolean, // *NOU*
    onClick: () -> Unit
) {
    // --- Stare pentru animație ---
    val scale = remember { Animatable(1f) } // Scala inițială 1.0
    val alpha = remember { Animatable(1f) } // Alpha inițial 1.0

    // --- Efect care rulează când isDisappearing devine true ---
    LaunchedEffect(isDisappearing) {
        if (isDisappearing) {
            // Lansează animațiile în paralel
            launch {
                scale.animateTo(
                    targetValue = 0.3f, // Se micșorează
                    animationSpec = tween(durationMillis = 300) // Durata animației
                )
            }
            launch {
                alpha.animateTo(
                    targetValue = 0f, // Devine transparent
                    animationSpec = tween(durationMillis = 300)
                )
            }
        } else {
            // Opcional: Resetează instant dacă nu dispare (de ex, dacă o potrivire e anulată)
            // scale.snapTo(1f)
            // alpha.snapTo(1f)
            // Sau animat înapoi, dar snap e probabil mai bun
        }
    }

    // --- Modificatori ---
    val selectionModifier = if (isSelected) {
        Modifier
            .border(
                width = 2.dp,
                color = Color.Yellow,
                shape = MaterialTheme.shapes.small
            )
            .scale(1.05f) // Scalarea de la selecție
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .size(size)
            .padding(1.dp)
            // *NOU:* Aplică scale și alpha animate folosind graphicsLayer
            .graphicsLayer(
                scaleX = scale.value,
                scaleY = scale.value,
                alpha = alpha.value
            )
            // Aplică selecția PESTE efectul de graphicsLayer
            .then(selectionModifier)
            .background(
                color = tileColors[type] ?: Color.Gray,
                shape = MaterialTheme.shapes.small
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // Conținut piesă (opțional)
    }
}



@Preview(showBackground = true, widthDp = 380, heightDp = 600)
@Composable
fun DefaultPreview() {
    Match3PuzzleGameTheme {
        GameScreen()
    }
}