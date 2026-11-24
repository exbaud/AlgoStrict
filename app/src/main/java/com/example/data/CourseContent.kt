package com.example.data

data class CourseExample(
    val id: String,
    val title: String,
    val description: String,
    val code: String
)

data class CourseChapter(
    val number: Int,
    val title: String,
    val subtitle: String,
    val keyRules: List<String>,
    val commonPitfalls: List<String>,
    val examples: List<CourseExample>
)

data class ExerciseTestCase(
    val label: String,
    val inputs: List<String>,
    val expectedSubstrings: List<String>
)

data class CourseExercise(
    val id: String,
    val serie: String,
    val title: String,
    val chapterRef: String,
    val difficulty: String,
    val prompt: String,
    val starterCode: String,
    val referenceSolution: String,
    val testCases: List<ExerciseTestCase>
)

data class CodeTemplate(
    val id: String,
    val title: String,
    val subtitle: String,
    val code: String
)

object CourseContent {

    val chapters: List<CourseChapter> = listOf(
        CourseChapter(
            number = 1,
            title = "Introduction aux algorithmes",
            subtitle = "Structure générale, variables, constantes et types de base",
            keyRules = listOf(
                "Tout algorithme commence par : Algorithme <nom> ;",
                "La partie déclarative précède Début : Const nom = valeur ; et Var x, y : type ;",
                "Les 5 types de base sont : entier, réel, caractère, booléen, chaîne.",
                "Un identificateur est composé de lettres, chiffres et '_', sans espaces.",
                "Chaque déclaration se termine obligatoirement par un point-virgule ';'."
            ),
            commonPitfalls = listOf(
                "Oublier le ';' après Algorithme <nom> ou après Var / Const.",
                "Mettre des espaces dans le nom de l'algorithme ou d'une variable.",
                "Confondre caractère ('a' entre apostrophes) et chaîne (\"bonjour\" entre guillemets)."
            ),
            examples = listOf(
                CourseExample(
                    id = "ch1_decl",
                    title = "Déclarations complètes",
                    description = "Exemple du cours (p. 10) présentant tous les types de base et constantes.",
                    code = """
Algorithme Demo_Declarations ;
Var x, y : entier ;
    z, w : réel ;
    lettre : caractère ;
    nom : chaîne ;
    Etat : booléen ;
Const n = 100 ;
      arobase = '@' ;
      mot = "bonjour" ;
Début
    x ← 10 ;
    y ← n + x ;
    z ← 12.5 ;
    lettre ← arobase ;
    nom ← mot ;
    Etat ← Vrai ;
    Ecrire (nom, " — y = ", y, " — z = ", z) ;
Fin
                    """.trimIndent()
                )
            )
        ),
        CourseChapter(
            number = 2,
            title = "Les instructions simples",
            subtitle = "Affectation (←), Lecture (Lire), Écriture (Ecrire) et opérateurs",
            keyRules = listOf(
                "L'affectation s'écrit exclusivement avec '←' (ou '<-') : variable ← expression ;",
                "Une constante ne figure jamais à gauche d'une affectation.",
                "Compatibilité stricte (p. 12) : réel ← entier est permis, mais entier ← réel ou entier ← caractère sont interdits.",
                "Opérateurs entiers : +, -, *, div (division entière), mod (reste). La division '/' donne un réel.",
                "Opérateurs logiques : NON, ET, OU, NON ET, NON OU. Comparaisons : >, >=, <, <=, =, ≠ (ou <>).",
                "Entrées/Sorties : Lire (var1, var2) ; et Ecrire (expr1, expr2) ; avec parenthèses obligatoires."
            ),
            commonPitfalls = listOf(
                "Utiliser '=' pour affecter (en pseudo-code '=' sert uniquement aux comparaisons).",
                "Utiliser les opérateurs du C : '==', '!=', '&&', '||', '%', printf, scanf.",
                "Tenter d'affecter un réel dans une variable de type entier."
            ),
            examples = listOf(
                CourseExample(
                    id = "ch2_moyenne",
                    title = "Moyenne de deux réels",
                    description = "Exemple complet du cours (p. 14) : lecture au clavier, calcul et affichage.",
                    code = """
Algorithme Moyenne_deux_reels ;
Var x, y, z : réel ;
Début
    Ecrire ("Donner la première valeur :") ;
    Lire (x) ;
    Ecrire ("Donner la deuxième valeur :") ;
    Lire (y) ;
    z ← (x + y) / 2 ;
    Ecrire ("La moyenne est : ", z) ;
Fin
                    """.trimIndent()
                )
            )
        ),
        CourseChapter(
            number = 3,
            title = "Les instructions conditionnelles",
            subtitle = "Forme simple, forme complète, tests imbriqués et Selon",
            keyRules = listOf(
                "Forme simple : Si (condition) Alors ... FinSi",
                "Forme complète : Si (condition) Alors ... Sinon ... FinSi",
                "Tests imbriqués : dans 'Sinon Si (...) Alors ...', chaque 'Si' / 'Sinon Si' possède son propre 'FinSi' (p. 17).",
                "Choix multiples : Selon (variable) val1 : ... défaut : ... FinSelon ;"
            ),
            commonPitfalls = listOf(
                "Oublier un 'FinSi' dans une chaîne de 'Sinon Si' imbriqués.",
                "Mettre une expression non booléenne (ex: un entier) dans la condition du 'Si'.",
                "Oublier le ':' après chaque valeur ou après 'défaut' dans 'Selon'."
            ),
            examples = listOf(
                CourseExample(
                    id = "ch3_eau",
                    title = "État de l'eau (tests imbriqués)",
                    description = "Exemple du cours (p. 17) illustrant l'imbrication stricte Sinon Si / FinSi.",
                    code = """
Algorithme Etat_Eau ;
Var t : réel ;
Début
    Ecrire ("Donner la température de l'eau :") ;
    Lire (t) ;
    Si (t <= 0) Alors
        Ecrire ("Etat solide") ;
    Sinon Si (t < 100) Alors
        Ecrire ("Etat liquide") ;
    Sinon
        Ecrire ("Etat gazeux") ;
    FinSi
    FinSi
Fin
                    """.trimIndent()
                ),
                CourseExample(
                    id = "ch3_selon",
                    title = "Jours de la semaine (Selon)",
                    description = "Exemple du cours (p. 20) utilisant la structure à choix multiples Selon.",
                    code = """
Algorithme Jour_Semaine ;
Var jour : entier ;
Début
    Ecrire ("Entrez un numéro de jour (1 à 7) :") ;
    Lire (jour) ;
    Selon (jour)
        1 : Ecrire ("Dimanche") ;
        2 : Ecrire ("Lundi") ;
        3 : Ecrire ("Mardi") ;
        4 : Ecrire ("Mercredi") ;
        5 : Ecrire ("Jeudi") ;
        6 : Ecrire ("Vendredi") ;
        7 : Ecrire ("Samedi") ;
        défaut : Ecrire ("Numéro de jour invalide.") ;
    FinSelon ;
Fin
                    """.trimIndent()
                )
            )
        ),
        CourseChapter(
            number = 4,
            title = "Les instructions itératives (boucles)",
            subtitle = "Pour, Tant que ... Faire, Répéter ... Jusqu'à, compteurs et accumulateurs",
            keyRules = listOf(
                "Boucle Pour : Pour indice de début à fin [Pas valeur] ... FinPour (sans mot-clé 'Faire').",
                "Si 'Pas' est omis : +1 si début <= fin, -1 si début > fin (p. 22).",
                "Boucle Tant que : Tant que (condition) Faire ... FinTq (ou FinTant que).",
                "Boucle Répéter : Répéter ... Jusqu'à (condition) ; — la condition est une CONDITION D'ARRÊT (contrairement au do..while du C) !"
            ),
            commonPitfalls = listOf(
                "Écrire 'Pour i ← 1 à N' au lieu de 'Pour i de 1 à N'.",
                "Inverser la logique de 'Jusqu'à' : la boucle s'arrête quand la condition devient Vraie.",
                "Oublier d'initialiser le compteur ou l'accumulateur avant la boucle."
            ),
            examples = listOf(
                CourseExample(
                    id = "ch4_somme",
                    title = "Somme de N valeurs (Accumulateur)",
                    description = "Exemple du cours (p. 25) calculant la somme de n réels saisis.",
                    code = """
Algorithme Somme_Valeurs ;
Var i, n : entier ;
    som, val : réel ;
Début
    Ecrire ("Donner le nombre de valeurs :") ;
    Lire (n) ;
    som ← 0 ;
    Pour i de 1 à n
        Ecrire ("Entrer une valeur :") ;
        Lire (val) ;
        som ← som + val ;
    FinPour
    Ecrire ("La somme des valeurs est égale à : ", som) ;
Fin
                    """.trimIndent()
                ),
                CourseExample(
                    id = "ch4_repeter",
                    title = "Carrés jusqu'à 0 (Répéter)",
                    description = "Exemple du cours (p. 23) montrant la condition d'arrêt (n = 0).",
                    code = """
Algorithme Boucle_Repeter ;
Var n, p : entier ;
Début
    Répéter
        Ecrire ("Donner un nombre (0 pour arrêter) :") ;
        Lire (n) ;
        p ← n * n ;
        Ecrire (p) ;
    Jusqu'à (n = 0) ;
    Ecrire ("Fin de l'algorithme") ;
Fin
                    """.trimIndent()
                )
            )
        ),
        CourseChapter(
            number = 5,
            title = "Les tableaux",
            subtitle = "Tableaux 1D, 2D, recherche avec drapeau, tri par sélection et insertion",
            keyRules = listOf(
                "Déclaration 1D : Tableau nom[taille] : type ; et 2D : Tableau nom[lignes][colonnes] : type ;",
                "Les indices commencent obligatoirement à 0 (de 0 à taille - 1).",
                "L'indice doit être strictement de type entier.",
                "La notion de drapeau (booléen initialisé à Faux) permet de savoir si une valeur existe après parcours."
            ),
            commonPitfalls = listOf(
                "Accéder à Tab[N] au lieu de Tab[N-1] (débordement d'indice).",
                "Afficher 'trouvé / non trouvé' à l'intérieur de la boucle au lieu d'utiliser un drapeau."
            ),
            examples = listOf(
                CourseExample(
                    id = "ch5_drapeau",
                    title = "Recherche avec drapeau (Flag)",
                    description = "Exemple du cours (p. 34) pour chercher une valeur dans un tableau.",
                    code = """
Algorithme Recherche_Drapeau ;
Const N = 6 ;
Tableau Tab[N] : entier ;
Var val, i : entier ;
    existe : booléen ;
Début
    Tab[0] ← 10 ; Tab[1] ← 22 ; Tab[2] ← 31 ;
    Tab[3] ← 46 ; Tab[4] ← 5 ; Tab[5] ← 7 ;
    Ecrire ("Entrez la valeur à rechercher :") ;
    Lire (val) ;
    existe ← Faux ;
    Pour i de 0 à N - 1
        Si (val = Tab[i]) Alors
            existe ← Vrai ;
        FinSi
    FinPour
    Si (existe) Alors
        Ecrire (val, " fait partie du tableau") ;
    Sinon
        Ecrire (val, " ne fait pas partie du tableau") ;
    FinSi
Fin
                    """.trimIndent()
                ),
                CourseExample(
                    id = "ch5_tri_insertion",
                    title = "Tri par insertion",
                    description = "Algorithme Tri_Insertion du cours (p. 39) sur un tableau de 8 entiers.",
                    code = """
Algorithme Tri_Insertion ;
Const n = 8 ;
Tableau T[n] : entier ;
Var i, j, cle : entier ;
Début
    T[0] ← 25 ; T[1] ← 10 ; T[2] ← 13 ; T[3] ← 31 ;
    T[4] ← 22 ; T[5] ← 4 ; T[6] ← 2 ; T[7] ← 18 ;
    Pour i de 1 à n - 1
        cle ← T[i] ;
        j ← i - 1 ;
        Tant que ((j >= 0) ET (cle < T[j])) Faire
            T[j + 1] ← T[j] ;
            j ← j - 1 ;
        FinTq
        T[j + 1] ← cle ;
    FinPour
    Ecrire ("Tableau trié :") ;
    Pour i de 0 à n - 1
        Ecrire (T[i]) ;
    FinPour
Fin
                    """.trimIndent()
                )
            )
        ),
        CourseChapter(
            number = 6,
            title = "Les enregistrements (structures)",
            subtitle = "Structure ... FinStructure, tableaux de structures et imbrication",
            keyRules = listOf(
                "Définition : Structure Nom ... champ : type ; ... FinStructure ;",
                "Accès à un champ via le point : x.num, T[i].nom",
                "Une structure imbriquée doit obligatoirement être déclarée AVANT la structure qui la contient (p. 42)."
            ),
            commonPitfalls = listOf(
                "Déclarer une structure interne après la structure englobante.",
                "Oublier 'FinStructure ;' ou utiliser 'Var' à l'intérieur de Structure."
            ),
            examples = listOf(
                CourseExample(
                    id = "ch6_struct",
                    title = "Structures Date et Compte",
                    description = "Exemple du cours (p. 42) avec structure membre d'une autre structure.",
                    code = """
Algorithme Gestion_Comptes ;
Structure Date
    jour, mois, annee : entier ;
FinStructure ;
Structure Compte
    Ncpt : entier ;
    nom : chaîne ;
    DtOuverture : Date ;
FinStructure ;
Tableau T[2] : Structure Compte ;
Var i : entier ;
Début
    T[0].Ncpt ← 101 ;
    T[0].nom ← "Amina" ;
    T[0].DtOuverture.jour ← 15 ;
    T[0].DtOuverture.mois ← 10 ;
    T[0].DtOuverture.annee ← 2025 ;
    Ecrire ("Compte ", T[0].Ncpt, " : ", T[0].nom, " ouvert en ", T[0].DtOuverture.annee) ;
Fin
                    """.trimIndent()
                )
            )
        ),
        CourseChapter(
            number = 7,
            title = "Les fonctions et les procédures",
            subtitle = "Sous-programmes, portée locale/globale, passage par valeur et récursivité",
            keyRules = listOf(
                "Fonction nom (params : types) : type_retour ... Retourner resultat ; Fin",
                "Procédure nom (params : types) ... Fin (ne retourne aucune valeur).",
                "Les parenthèses '()' sont toujours obligatoires à la définition et à l'appel.",
                "Une variable locale masque la variable globale de même nom (p. 44).",
                "Le passage par valeur copie l'argument : la variable appelante n'est pas modifiée.",
                "Une fonction récursive doit posséder une condition d'arrêt."
            ),
            commonPitfalls = listOf(
                "Oublier 'Retourner' dans une Fonction, ou mettre 'Retourner x' dans une Procédure.",
                "Appeler une Fonction comme une instruction isolée sans récupérer son résultat."
            ),
            examples = listOf(
                CourseExample(
                    id = "ch7_fact",
                    title = "Factorielle récursive",
                    description = "Fonction récursive FACT du cours (p. 50).",
                    code = """
Algorithme Calcul_Factorielle ;
Var n, res : entier ;

Fonction FACT (k : entier) : entier
Début
    Si (k = 0) Alors
        Retourner 1 ;
    Sinon
        Retourner k * FACT (k - 1) ;
    FinSi
Fin

Début
    Ecrire ("Donner un entier >= 0 :") ;
    Lire (n) ;
    res ← FACT (n) ;
    Ecrire (n, "! = ", res) ;
Fin
                    """.trimIndent()
                ),
                CourseExample(
                    id = "ch7_portee",
                    title = "Portée des variables",
                    description = "Exemple du cours (p. 45) illustrant le masquage d'une variable globale.",
                    code = """
Algorithme Principal ;
Var x, y : entier ;

Procédure Secondaire ()
Var x : entier ;
Début
    x ← 3 ;
    Ecrire ("Dans Secondaire : x=", x, " y=", y) ;
Fin

Début
    x ← 5 ;
    y ← 8 ;
    Secondaire () ;
    Ecrire ("Dans Principal : x=", x, " y=", y) ;
Fin
                    """.trimIndent()
                )
            )
        ),
        CourseChapter(
            number = 8,
            title = "Les pointeurs",
            subtitle = "Adresses (&), déréférencement (*), NIL, allocation dynamique et passage par adresse",
            keyRules = listOf(
                "Déclaration : p : *entier ; (ou p : pointeur sur entier ;).",
                "Initialisation obligatoire avant utilisation : p ← NIL ; ou p ← &x ; ou p ← nouveau entier ;",
                "Opérateur '&' obtient l'adresse d'une variable ; '*' accède au contenu pointé.",
                "Allocation dynamique : p ← nouveau Type ; et libération : Libérer p ; p ← NIL ;",
                "Le passage par adresse permet à un sous-programme de modifier une variable de l'appelant."
            ),
            commonPitfalls = listOf(
                "Déréférencer (*p) un pointeur non initialisé ou égal à NIL.",
                "Accéder à une zone mémoire après 'Libérer p ;'."
            ),
            examples = listOf(
                CourseExample(
                    id = "ch8_pointeur",
                    title = "Manipulation de pointeurs",
                    description = "Exemple complet du cours (p. 55) — observez la mémoire en mode Pas-à-pas !",
                    code = """
Algorithme Exemple_pointeur ;
Var x : entier ;
    p1, p2 : *entier ;
Début
    x ← 3 ;
    p1 ← &x ;
    p2 ← NIL ;
    Ecrire ("Contenu pointé par p1 : ", *p1) ;
    *p1 ← 5 ;
    Ecrire ("x=", x, " *p1=", *p1) ;
    p2 ← p1 ;
    Ecrire ("Contenu pointé par p2 : ", *p2) ;
Fin
                    """.trimIndent()
                ),
                CourseExample(
                    id = "ch8_adresse",
                    title = "Passage par adresse",
                    description = "Algorithme D du cours (p. 58) modifiant x via son adresse &x.",
                    code = """
Algorithme Passage_Adresse ;
Var x : entier ;

Procédure Modif (px : *entier)
Début
    *px ← *px + 1 ;
Fin

Début
    x ← 1 ;
    Ecrire ("Valeur de x avant l'appel : ", x) ;
    Modif (&x) ;
    Ecrire ("Valeur de x après l'appel : ", x) ;
Fin
                    """.trimIndent()
                )
            )
        )
    )

    val exercises: List<CourseExercise> = listOf(
        CourseExercise(
            id = "s2_ex3",
            serie = "Série 2 — Exo 3",
            title = "Permutation de deux variables",
            chapterRef = "Chap. 2",
            difficulty = "Facile",
            prompt = "Écrire un algorithme qui lit deux entiers a et b, permute leurs valeurs puis les affiche (ex: avant a=5, b=7 -> après 7 puis 5).",
            starterCode = """
Algorithme Permutation ;
Var a, b, temp : entier ;
Début
    Lire (a, b) ;
    // Effectuez la permutation ici
    
    Ecrire ("Après : a=", a, " b=", b) ;
Fin
            """.trimIndent(),
            referenceSolution = """
Algorithme Permutation ;
Var a, b, temp : entier ;
Début
    Lire (a, b) ;
    temp ← a ;
    a ← b ;
    b ← temp ;
    Ecrire ("Après : a=", a, " b=", b) ;
Fin
            """.trimIndent(),
            testCases = listOf(
                ExerciseTestCase("a=5, b=7", listOf("5", "7"), listOf("7", "5")),
                ExerciseTestCase("a=-3, b=12", listOf("-3", "12"), listOf("12", "-3"))
            )
        ),
        CourseExercise(
            id = "s3_ex2",
            serie = "Série 3 — Exo 2",
            title = "Nombre pair ou impair",
            chapterRef = "Chap. 3",
            difficulty = "Facile",
            prompt = "Écrire un algorithme qui lit un entier n et affiche \"Pair\" s'il est pair ou \"Impair\" sinon (en utilisant mod).",
            starterCode = """
Algorithme Parite ;
Var n : entier ;
Début
    Lire (n) ;
    
Fin
            """.trimIndent(),
            referenceSolution = """
Algorithme Parite ;
Var n : entier ;
Début
    Lire (n) ;
    Si (n mod 2 = 0) Alors
        Ecrire ("Pair") ;
    Sinon
        Ecrire ("Impair") ;
    FinSi
Fin
            """.trimIndent(),
            testCases = listOf(
                ExerciseTestCase("n=8", listOf("8"), listOf("Pair")),
                ExerciseTestCase("n=15", listOf("15"), listOf("Impair"))
            )
        ),
        CourseExercise(
            id = "s3_ex5",
            serie = "Série 3 — Exo 5b",
            title = "Signe d'un produit sans le calculer",
            chapterRef = "Chap. 3",
            difficulty = "Moyen",
            prompt = "Lire deux réels a et b et afficher si leur produit est \"Nul\", \"Positif\" ou \"Négatif\", sans effectuer la multiplication.",
            starterCode = """
Algorithme Signe_Produit ;
Var a, b : réel ;
Début
    Lire (a, b) ;
    
Fin
            """.trimIndent(),
            referenceSolution = """
Algorithme Signe_Produit ;
Var a, b : réel ;
Début
    Lire (a, b) ;
    Si ((a = 0) OU (b = 0)) Alors
        Ecrire ("Nul") ;
    Sinon Si (((a > 0) ET (b > 0)) OU ((a < 0) ET (b < 0))) Alors
        Ecrire ("Positif") ;
    Sinon
        Ecrire ("Négatif") ;
    FinSi
    FinSi
Fin
            """.trimIndent(),
            testCases = listOf(
                ExerciseTestCase("a=0, b=5", listOf("0", "5"), listOf("Nul")),
                ExerciseTestCase("a=-4, b=-2", listOf("-4", "-2"), listOf("Positif")),
                ExerciseTestCase("a=-4, b=3", listOf("-4", "3"), listOf("Négatif"))
            )
        ),
        CourseExercise(
            id = "s4_ex4",
            serie = "Série 4 — Exo 4",
            title = "Somme des entiers jusqu'à n",
            chapterRef = "Chap. 4",
            difficulty = "Facile",
            prompt = "Lire un entier n et afficher uniquement la somme 1 + 2 + ... + n.",
            starterCode = """
Algorithme Somme_Entiers ;
Var i, n, s : entier ;
Début
    Lire (n) ;
    s ← 0 ;
    
    Ecrire (s) ;
Fin
            """.trimIndent(),
            referenceSolution = """
Algorithme Somme_Entiers ;
Var i, n, s : entier ;
Début
    Lire (n) ;
    s ← 0 ;
    Pour i de 1 à n
        s ← s + i ;
    FinPour
    Ecrire (s) ;
Fin
            """.trimIndent(),
            testCases = listOf(
                ExerciseTestCase("n=5", listOf("5"), listOf("15")),
                ExerciseTestCase("n=10", listOf("10"), listOf("55"))
            )
        ),
        CourseExercise(
            id = "s4_ex7",
            serie = "Série 4 — Exo 7",
            title = "Test de primalité",
            chapterRef = "Chap. 4",
            difficulty = "Moyen",
            prompt = "Lire un entier n (> 1) et afficher \"Premier\" s'il est premier, ou \"Non premier\" sinon.",
            starterCode = """
Algorithme Nombre_Premier ;
Var n, i : entier ;
    estPremier : booléen ;
Début
    Lire (n) ;
    
Fin
            """.trimIndent(),
            referenceSolution = """
Algorithme Nombre_Premier ;
Var n, i : entier ;
    estPremier : booléen ;
Début
    Lire (n) ;
    Si (n <= 1) Alors
        estPremier ← Faux ;
    Sinon
        estPremier ← Vrai ;
        Pour i de 2 à n div 2
            Si (n mod i = 0) Alors
                estPremier ← Faux ;
            FinSi
        FinPour
    FinSi
    Si (estPremier) Alors
        Ecrire ("Premier") ;
    Sinon
        Ecrire ("Non premier") ;
    FinSi
Fin
            """.trimIndent(),
            testCases = listOf(
                ExerciseTestCase("n=13", listOf("13"), listOf("Premier")),
                ExerciseTestCase("n=12", listOf("12"), listOf("Non premier"))
            )
        ),
        CourseExercise(
            id = "s5_ex2",
            serie = "Série 5 — Exo 2a",
            title = "Maximum d'un tableau et sa position",
            chapterRef = "Chap. 5",
            difficulty = "Moyen",
            prompt = "Lire 5 entiers dans un tableau T[5], puis afficher le maximum et son indice (à partir de 0).",
            starterCode = """
Algorithme Max_Tableau ;
Tableau T[5] : entier ;
Var i, max, pos : entier ;
Début
    Pour i de 0 à 4
        Lire (T[i]) ;
    FinPour
    
Fin
            """.trimIndent(),
            referenceSolution = """
Algorithme Max_Tableau ;
Tableau T[5] : entier ;
Var i, max, pos : entier ;
Début
    Pour i de 0 à 4
        Lire (T[i]) ;
    FinPour
    max ← T[0] ;
    pos ← 0 ;
    Pour i de 1 à 4
        Si (T[i] > max) Alors
            max ← T[i] ;
            pos ← i ;
        FinSi
    FinPour
    Ecrire ("Max=", max, " Pos=", pos) ;
Fin
            """.trimIndent(),
            testCases = listOf(
                ExerciseTestCase("T=[4, 19, 7, 2, 11]", listOf("4", "19", "7", "2", "11"), listOf("19", "1"))
            )
        ),
        CourseExercise(
            id = "s6_ex2",
            serie = "Série 6 — Exo 2",
            title = "Fonctions Min et Max",
            chapterRef = "Chap. 7",
            difficulty = "Moyen",
            prompt = "Écrire une fonction Max(a, b) qui retourne le maximum de deux réels, l'appeler sur deux nombres lus au clavier et afficher le résultat.",
            starterCode = """
Algorithme Test_Max ;
Var x, y, m : réel ;

Fonction Max (a : réel, b : réel) : réel
Début
    
Fin

Début
    Lire (x, y) ;
    m ← Max (x, y) ;
    Ecrire (m) ;
Fin
            """.trimIndent(),
            referenceSolution = """
Algorithme Test_Max ;
Var x, y, m : réel ;

Fonction Max (a : réel, b : réel) : réel
Début
    Si (a >= b) Alors
        Retourner a ;
    Sinon
        Retourner b ;
    FinSi
Fin

Début
    Lire (x, y) ;
    m ← Max (x, y) ;
    Ecrire (m) ;
Fin
            """.trimIndent(),
            testCases = listOf(
                ExerciseTestCase("x=4.5, y=9.2", listOf("4.5", "9.2"), listOf("9.2")),
                ExerciseTestCase("x=12, y=3", listOf("12", "3"), listOf("12"))
            )
        )
    )

    val templates: List<CodeTemplate> = listOf(
        CodeTemplate(
            id = "tpl_empty",
            title = "Algorithme vierge",
            subtitle = "Squelette strict minimal (Chap. 1)",
            code = """
Algorithme Mon_Algorithme ;
Var x : entier ;
Début
    x ← 0 ;
    Ecrire ("Valeur = ", x) ;
Fin
            """.trimIndent()
        ),
        CodeTemplate(
            id = "tpl_cond",
            title = "Alternative complète",
            subtitle = "Structure Si ... Alors ... Sinon ... FinSi",
            code = """
Algorithme Test_Age ;
Var age : entier ;
Début
    Ecrire ("Entrez votre âge :") ;
    Lire (age) ;
    Si (age >= 18) Alors
        Ecrire ("Majeur") ;
    Sinon
        Ecrire ("Mineur") ;
    FinSi
Fin
            """.trimIndent()
        ),
        CodeTemplate(
            id = "tpl_array",
            title = "Tableau et boucle Pour",
            subtitle = "Lecture et parcours 0..N-1 (Chap. 5)",
            code = """
Algorithme Parcours_Tableau ;
Const N = 4 ;
Tableau Tab[N] : entier ;
Var i, som : entier ;
Début
    som ← 0 ;
    Pour i de 0 à N - 1
        Ecrire ("Donner Tab[", i, "] :") ;
        Lire (Tab[i]) ;
        som ← som + Tab[i] ;
    FinPour
    Ecrire ("Somme = ", som) ;
Fin
            """.trimIndent()
        ),
        CodeTemplate(
            id = "tpl_pointer",
            title = "Pointeur et allocation dynamique",
            subtitle = "nouveau, déréférencement * et Libérer (Chap. 8)",
            code = """
Algorithme Demo_Allocation ;
Var p : *entier ;
Début
    p ← nouveau entier ;
    *p ← 12345 ;
    Ecrire ("Le contenu de p est : ", *p) ;
    Libérer p ;
    p ← NIL ;
Fin
            """.trimIndent()
        )
    )
}
