// ATENȚIE: Asigură-te că acest nume de pachet se potrivește cu structura proiectului tău!
package com.example.match3puzzlegame
// Test GitHub undeva în MainActivity.kt).
// --- Importuri Esențiale ---
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
import com.example.match3puzzlegame.ui.theme.Match3PuzzleGameTheme // Import tema
import kotlin.math.abs // Import pentru valoare absolută

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

// --- Clasa pentru Poziție --- *NOU*
data class TilePosition(val row: Int, val col: Int)

// --- TAG pentru Logcat --- *NOU*
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
    var board by remember {
        mutableStateOf(
            List(ROWS) {
                MutableList(COLS) {
                    TILE_TYPES.random()
                }
            }
        )
    }
    // --- Starea pentru Selecție --- *NOU*
    var selectedTilePos by remember { mutableStateOf<TilePosition?>(null) }

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

    fun findMatches(): Set<TilePosition> {
        val matches = mutableSetOf<TilePosition>()
        val currentBoard = board // Lucrează cu starea curentă

        // Verificare Orizontală
        for (r in 0 until ROWS) {
            var currentStreak = 1
            var currentType = -1 // Tip invalid inițial
            for (c in 0 until COLS) {
                val tileType = currentBoard.getOrNull(r)?.getOrNull(c) ?: EMPTY_TILE

                if (tileType != EMPTY_TILE && tileType == currentType) {
                    currentStreak++
                } else {
                    // Verifică dacă streak-ul anterior a fost o potrivire
                    if (currentStreak >= 3) {
                        for (i in 1..currentStreak) {
                            matches.add(TilePosition(r, c - i))
                        }
                    }
                    // Resetează pentru piesa curentă (dacă nu e goală)
                    currentType = tileType
                    currentStreak = if (tileType != EMPTY_TILE) 1 else 0
                }
            }
            // Verifică streak-ul de la sfârșitul rândului
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
                val tileType = currentBoard.getOrNull(r)?.getOrNull(c) ?: EMPTY_TILE

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
        Log.d(TAG, "findMatches found: ${matches.size} tiles")
        return matches
    }

    // Adaugă și această funcție în @Composable fun GameScreen()

    fun processMatches(matchedTiles: Set<TilePosition>) {
        if (matchedTiles.isEmpty()) return // Nu face nimic dacă nu sunt potriviri

        // 1. Calculează scorul (simplu: 10 puncte per piesă)
        val pointsEarned = matchedTiles.size * 10
        score += pointsEarned
        Log.d(TAG, "Match processed: ${matchedTiles.size} tiles, +$pointsEarned score. New score: $score")

        // 2. Actualizează feedback-ul
        feedbackMessage = "Potrivire de ${matchedTiles.size}! +$pointsEarned stele!"

        // 3. Creează o nouă tablă cu piesele potrivite eliminate (înlocuite cu EMPTY_TILE)
        val newBoard = board.map { it.toMutableList() }
        matchedTiles.forEach { pos ->
            // Verificare suplimentară a limitelor, deși nu ar trebui să fie necesară dacă findMatches e corect
            if (pos.row in 0 until ROWS && pos.col in 0 until COLS) {
                newBoard[pos.row][pos.col] = EMPTY_TILE
            } else {
                Log.w(TAG, "processMatches: Coordonată invalidă în setul de potriviri: $pos")
            }
        }

        // 4. Actualizează starea tablei
        board = newBoard

        // TODO: Aici vom adăuga logica pentru căderea pieselor (gravity)
    }

    // --- Funcție Helper pentru Swap --- *NOU*
    fun swapTiles(pos1: TilePosition, pos2: TilePosition) {
        Log.d(TAG, "swapTiles called for $pos1 and $pos2")
        val newBoard = board.map { it.toMutableList() } // Copie profundă mutabilă
        try {
            val temp = newBoard[pos1.row][pos1.col]
            newBoard[pos1.row][pos1.col] = newBoard[pos2.row][pos2.col]
            newBoard[pos2.row][pos2.col] = temp

            board = newBoard // Actualizează starea

            // Acum verifică potrivirile rezultate din swap
            val matches = findMatches()
            if (matches.isNotEmpty()) {
                processMatches(matches)
            } else {
                // --- IMPORTANT: Dacă swap-ul NU creează potriviri, anulează-l! ---
                // Altfel, jucătorul poate face mutări inutile.
                // (Opțional, poți adăuga o mică animație de "nu" aici)
                Log.d(TAG, "Swap invalid, reverting.")
                feedbackMessage = "Mutare invalidă!"
                // Refacem swap-ul înapoi
                val revertedBoard = newBoard.map { it.toMutableList() } // Copie din nou
                val temp = revertedBoard[pos1.row][pos1.col]
                revertedBoard[pos1.row][pos1.col] = revertedBoard[pos2.row][pos2.col]
                revertedBoard[pos2.row][pos2.col] = temp
                board = revertedBoard // Revino la starea de dinainte de swap
            }


            feedbackMessage = "Swap între (${pos1.row},${pos1.col}) și (${pos2.row},${pos2.col})"
            // TODO: Verifică potrivirile după swap
        } catch (e: IndexOutOfBoundsException) {
            Log.e(TAG, "Eroare la swap: Index în afara limitelor! pos1=$pos1, pos2=$pos2", e)
            feedbackMessage = "Eroare internă la swap!"
        }
    }

    // Adaugă această funcție în interiorul @Composable fun GameScreen(),
// la același nivel cu areAdjacent și swapTiles.



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
            selectedTilePosition = selectedTilePos, // Pasează starea de selecție *MODIFICAT*
            onTileClick = { row, col -> // Logica de click *MODIFICAT*
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
    }
}

@Composable
fun GameBoard(
    board: List<List<Int>>,
    selectedTilePosition: TilePosition?, // Primește poziția selectată *MODIFICAT*
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
                        GameTile(
                            type = tileType,
                            size = tileSize,
                            isSelected = isSelected, // Pasează starea de selecție *MODIFICAT*
                            onClick = { onTileClick(rowIndex, colIndex) }
                        )
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
    isSelected: Boolean, // Primește starea de selecție *MODIFICAT*
    onClick: () -> Unit
) {
    // Definire efect vizual pentru selecție *MODIFICAT*
    val tileModifier = Modifier
        .size(size)
        .padding(1.dp) // Ajustează padding-ul dacă vrei spațiu între piese
        .then(
            if (isSelected) {
                Modifier
                    .border( // Bordură galbenă groasă la selecție
                        width = 2.dp, // Îngroșăm puțin bordura
                        color = Color.Yellow,
                        shape = MaterialTheme.shapes.small
                    )
                    .scale(1.05f) // Mărește ușor piesa selectată
            } else {
                Modifier // Fără modificări extra dacă nu e selectată (bordura implicită dispare)
                // Dacă vrei o bordură subțire mereu, adaug-o aici:
                // .border(
                //     width = 0.5.dp,
                //     color = Color.Black.copy(alpha = 0.1f),
                //     shape = MaterialTheme.shapes.small
                // )
            }
        )
        .background(
            color = tileColors[type] ?: Color.Gray,
            shape = MaterialTheme.shapes.small
        )
        .clickable(onClick = onClick) // Click handler aplicat la sfârșit

    Box(
        modifier = tileModifier, // Aplică modificatorul compus
        contentAlignment = Alignment.Center
    ) {
        // Conținut piesă (opțional)
    }
}


// --- Preview ---
@Preview(showBackground = true, widthDp = 380, heightDp = 600)
@Composable
fun DefaultPreview() {
    Match3PuzzleGameTheme {
        GameScreen()
    }
}