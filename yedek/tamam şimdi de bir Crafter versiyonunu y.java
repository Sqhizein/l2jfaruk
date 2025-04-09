tamam şimdi de bir Crafter versiyonunu. dikkat ayrı bir java dosyası istemiyorum. mevcut projeye ekleyelim. yani buna devam edelim C:\aa\l2faruk\dist\game\data\scripts\quests\Q00802_ticaretyapanPcTrade\Q00802_ticaretyapanPcTrade.java
bu faketraderlaır için C:\aa\l2faruk\dist\game\data\Recipes.xml dosyası ile işin var genelde
fakecrafter diyebiliriz
bunlarda pazara oturabilirlar ama bişey satmak veya almak yerine craft yaparlar


onların database yapısı biraz farklıdır aşağıda açıklayacağım


accounts tablosu
kaka	7XDFfXVk6ZTn1fb9aWfOqLNH77w=		2025-04-07 21:10:10	1744054941129	0	127.0.0.1	2	192.168.1.114	0.0.0.0	0.0.0.0	0.0.0.0	0.0.0.0		0					


characters
account_name	charId	char_name	level	maxHp	curHp	maxCp	curCp	maxMp	curMp	face	hairStyle	hairColor	sex	heading	x	y	z	exp	expBeforeDeath	sp	karma	fame	pvpkills	pkkills	clanid	race	classid	base_class	transform_id	deletetime	cancraft	title	title_color	accesslevel	online	onlinetime	char_slot	newbie	lastAccess	clan_privs	wantspeace	isin7sdungeon	power_grade	nobless	subpledge	lvl_joined_academy	apprentice	sponsor	clan_join_expiry_time	clan_create_expiry_time	death_penalty_level	bookmarkslot	vitality_points	createDate	language	faction	pccafe_points
kaka	268529192	fffffff	85	7052	7052	5521	4901	1814	1814	0	0	0	0	17238	82644	148035	-3477	16890558727	0	1000	0	0	0	0	0	4	118	118	0	0	0		15530402	0	2	0		1	1744133328008	0	0	0	0	0	0	0	0	0	0	0	0	0	20000	2025-04-07		0	40


character_offline_trade tablosu
charId	time	type	title
268529192	1744133828912	5	Come Craft


character_offline_trade_items tablosu
charId	item	count	price
268529192	673	0	2535
268529192	631	0	3464
268529192	630	0	3634
268529192	633	0	5336
268529192	667	0	14234
268529192	668	0	23512
268529192	675	0	23535
268529192	677	0	25235
268529192	626	0	34633
268529192	628	0	34645
268529192	676	0	35255
268529192	678	0	52352
268529192	632	0	53346
268529192	674	0	235235
268529192	671	0	235235
268529192	669	0	235262
268529192	672	0	252352
268529192	665	0	363323
268529192	679	0	52252325
268529192	666	0	623562345

(bir karakter 250 taneye kada recipe kapasitesine sahipti ama pazara en fazla 20 tane koyabilirler buna dikkat etmelisin)

character_recipebook tablosu
charId	id	classIndex	type
268529192	675	0	1
268529192	659	0	1
268529192	660	0	1
268529192	668	0	1
268529192	669	0	1
268529192	671	0	1
268529192	679	0	1
268529192	661	0	1
268529192	663	0	1
268529192	664	0	1
268529192	667	0	1
268529192	665	0	1
268529192	673	0	1
268529192	666	0	1
268529192	678	0	1
268529192	677	0	1
268529192	674	0	1
268529192	676	0	1
268529192	658	0	1
268529192	657	0	1
268529192	647	0	1
268529192	648	0	1
268529192	646	0	1
268529192	633	0	1
268529192	649	0	1
268529192	672	0	1
268529192	650	0	1
268529192	632	0	1
268529192	631	0	1
268529192	630	0	1
268529192	626	0	1
268529192	656	0	1
268529192	655	0	1
268529192	628	0	1
268529192	652	0	1
268529192	651	0	1



xml item dosyalarında şuna sahiptir bu itemler etcitem_type RECIPE dir
<set name="etcitem_type" val="RECIPE" />

yani bu fakecrafter a sadece RECIPE itemi verilmelidir

Craft işleminden alınması gereken adena miktarınıda ayarlayabilelim
örneğin
ama dikkat et Craft ücretini baz alacağımız şey itemin kendisidir. RECIPE iteminden bahsetmiyorum
örneğin

<item id="6579" type="Weapon" name="Arcana Mace">
<set name="price" val="64139100" />

 iteminin Recipe itemi aşağıdaki dosyada yani Recipes.xml dosyasında bulunur

mesela bu itemin Recipe.xml dosyasında id si 6899 dür ama %60 success rateli versiyonu. 100% success rateli versiyonu ise 6900 dır.

şimdi sana verdiğim bilgilere göre Eğer bir fakecrafter "6900" ile 100% success rate ile craft yapıyorsa Arcana Mace iteminin temel fiyatının Yarısını istesin yani price 64139100 diyor arcana mace iteminin. 64139100 / 2 = 32069550 adena alacak craft işleminden.
şimdi sana verdiğim bilgilere göre Eğer bir fakecrafter "6899" ile 60% success rate ile craft yapıyorsa Arcana Mace iteminin temel fiyatının Çeyreğini istesin yani price 64139100 diyor arcana mace iteminin. 64139100 / 4 = 16034825 adena alacak craft işleminden.
sana verrdiğim bu fiyatlama örneği recipe.xml dosyasında bulunan bütün itemler için geçerli olmalıdır.

ama config ayarlarınıda istiyorum aşağıda. oradan belki istisna ayarlar yapabilirim
şu itemlei istisna fiyatlama yapabilirim config kısmında . ama default olarak onlarda item fiyatının yarısını istesin ben ellemedğim sürece

bunlar soulshot ve spiritshot itemleri
<item id="20" recipeId="1804" name="mk_soulshot_d" craftLevel="2" type="dwarven" successRate="100">
<item id="21" recipeId="1805" name="mk_soulshot_c" craftLevel="4" type="dwarven" successRate="100">
<item id="22" recipeId="1806" name="mk_soulshot_b" craftLevel="6" type="dwarven" successRate="100">
<item id="23" recipeId="1807" name="mk_soulshot_a" craftLevel="7" type="dwarven" successRate="100">
<item id="24" recipeId="1808" name="mk_soulshot_s" craftLevel="8" type="dwarven" successRate="100">

<item id="323" recipeId="3953" name="mk_blessed_spiritshot_d" craftLevel="2" type="dwarven" successRate="100">
<item id="324" recipeId="3954" name="mk_blessed_spiritshot_c" craftLevel="4" type="dwarven" successRate="100">
<item id="325" recipeId="3955" name="mk_blessed_spiritshot_b" craftLevel="6" type="dwarven" successRate="100">
<item id="326" recipeId="3956" name="mk_blessed_spiritshot_a" craftLevel="7" type="dwarven" successRate="100">
<item id="327" recipeId="3957" name="mk_blessed_spiritshot_s" craftLevel="8" type="dwarven" successRate="100">


C:\aa\l2faruk\dist\game\data\Recipes.xml
<item id="644" recipeId="6899" name="mk_arcana_mace_i" craftLevel="9" type="dwarven" successRate="60">
<ingredient id="6899" count="1" />
<ingredient id="6697" count="17" />
<ingredient id="5554" count="4" />
<ingredient id="1890" count="154" />
<ingredient id="1888" count="154" />
<ingredient id="5550" count="308" />
<ingredient id="1885" count="77" />
<ingredient id="4042" count="77" />
<ingredient id="1462" count="211" />
<ingredient id="2134" count="43" />
<production id="6579" count="1" />
<productionRare id="12455" count="1" rarity="4" />
<statUse name="MP" value="225" />
</item>
<item id="645" recipeId="6900" name="mk_arcana_mace_ii" craftLevel="9" type="dwarven" successRate="100">
<ingredient id="6900" count="1" />
<ingredient id="6697" count="17" />
<ingredient id="5554" count="8" />
<ingredient id="1890" count="284" />
<ingredient id="1888" count="284" />
<ingredient id="5550" count="568" />
<ingredient id="1885" count="142" />
<ingredient id="4042" count="142" />
<ingredient id="1462" count="398" />
<ingredient id="2134" count="43" />
<production id="6579" count="1" />
<productionRare id="12455" count="1" rarity="4" />
<statUse name="MP" value="225" />
</item>

şu ayarları istiyorum

// Specialized crafter configs - Trader type distribution (percentage)

static final int INITIAL_CRAFTER_TRADER_COUNT = 200; // Number of Craft CRAFTER to spawn initially

static final int CRAFTER_MIN_CRAFT_NON_STACKABLE_ITEM_TYPES = 1; // Minimum number of different items a seller can offer
static final int CRAFTER_MAX_CRAFT_NON_STACKABLE_ITEM_TYPES = 1; // Maximum number of different items a seller can offer
static final int CRAFTER_MIN_CRAFT_NON_STACKABLE_ITEM_COUNT = 1; // Minimum quantity of a specific item
static final int CRAFTER_MAX_CRAFT_NON_STACKABLE_ITEM_COUNT = 20; // Maximum quantity of a specific item

static final int CRAFTER_MIN_CRAFT_STACKABLE_ITEM_COUNT = 1000; // Minimum quantity of a specific item
static final int CRAFTER_MAX_CRAFT_STACKABLE_ITEM_COUNT = 100000; // Maximum quantity of a specific item



static final int WEAPON_CRAFTER_PERCENTAGE = 25; // Weapon-specialized CRAFTER
static final int ARMOR_CRAFTER_PERCENTAGE = 25; // Armor-specialized CRAFTER
static final int ETCITEM_CRAFTER_PERCENTAGE = 25; // Etc item-specialized CRAFTER
static final int SOUL_AND_SPIRIT_SHOT_CRAFTER_PERCENTAGE = 25; // Craft item-specialized CRAFTER

static final int CRAFTER_PRICE_MULTIPLIER_SOUL_AND_SPIRIT_SHOT = 100; // % of reference price for soul and spirit items
static final int CRAFTER_PRICE_MULTIPLIER_MATS = 80; // % of reference price for no-grade items
static final int CRAFTER_PRICE_MULTIPLIER_NO_GRADE = 80; // % of reference price for no-grade items
static final int CRAFTER_PRICE_MULTIPLIER_D_GRADE = 70; // % of reference price for D-grade items
static final int CRAFTER_PRICE_MULTIPLIER_C_GRADE = 60; // % of reference price for C-grade items
static final int CRAFTER_PRICE_MULTIPLIER_B_GRADE = 50; // % of reference price for B-grade items
static final int CRAFTER_PRICE_MULTIPLIER_A_GRADE = 45; // % of reference price for A-grade items
static final int CRAFTER_PRICE_MULTIPLIER_S_GRADE = 40; // % of reference price for S-grade items
static final int CRAFTER_PRICE_MULTIPLIER_S80_GRADE = 35; // % of reference price for S80-grade items
static final int CRAFTER_PRICE_MULTIPLIER_S84_GRADE = 30; // % of reference price for S84-grade items







Adding Fake Crafters to Trader System
I'll implement the fake crafter functionality to your existing trader system. These crafters will sit in the marketplace and offer crafting services.

Steps to Implement:
Add configuration parameters for crafters
Create recipe data loading mechanism
Implement crafter-specific trader creation
Add recipe selection logic for different crafter types
Handle database entries correctly for crafters
Q00802_ticaretyapanPcTrade.java
Q00802_ticaretyapanPcTrade.javaApplying edits (3%)...
Explanation
I've implemented the fake crafter functionality with these key features:

Recipe Data Loading: Added a system to load recipe data from Recipes.xml with proper parsing of ingredients, success rates, and craft levels.

Pricing System: Implemented the pricing logic you requested - 1/2 of item price for 100% success recipes and 1/4 for lower success rate recipes, with grade-specific multipliers.

Specialized Crafters: Created four types of crafters:

Weapon crafters
Armor crafters
EtcItem crafters
Soul/Spirit Shot crafters
Database Integration: Added proper database handling for crafters:

Sets character_offline_trade with type=5 for manufacture
Adds recipes to character_recipebook
Adds crafter entries to character_offline_trade_items with proper pricing
Configuration Options: Added all the requested configuration parameters to control pricing, crafter count, and item selection.

The crafters will spawn in the marketplace alongside sellers, sitting and offering their crafting services at prices determined by the items they can craft. They will use the same name selection and appearance system as the existing traders, but will always be Dwarves (the crafting race).























2025.04.09 01:25:26,493	INFO	3	quests.Q00802_ticaretyapanPcTrade.Q00802_ticaretyapanPcTrade	Loading recipe data from Recipes.xml...
2025.04.09 01:25:26,498	WARNING	3	quests.Q00802_ticaretyapanPcTrade.Q00802_ticaretyapanPcTrade	Skipping recipe ID 1666 - not found in game's RecipeData
2025.04.09 01:25:26,498	WARNING	3	quests.Q00802_ticaretyapanPcTrade.Q00802_ticaretyapanPcTrade	Skipping recipe ID 1786 - not found in game's RecipeData
2025.04.09 01:25:26,498	WARNING	3	quests.Q00802_ticaretyapanPcTrade.Q00802_ticaretyapanPcTrade	Skipping recipe ID 1787 - not found in game's RecipeData
2025.04.09 01:25:26,498	WARNING	3	quests.Q00802_ticaretyapanPcTrade.Q00802_ticaretyapanPcTrade	Skipping recipe ID 1788 - not found in game's RecipeData
2025.04.09 01:25:26,498	WARNING	3	quests.Q00802_ticaretyapanPcTrade.Q00802_ticaretyapanPcTrade	Skipping recipe ID 1789 - not found in game's RecipeData
2025.04.09 01:25:26,498	WARNING	3	quests.Q00802_ticaretyapanPcTrade.Q00802_ticaretyapanPcTrade	Skipping recipe ID 1790 - not found in game's RecipeData
2025.04.09 01:25:26,498	WARNING	3	quests.Q00802_ticaretyapanPcTrade.Q00802_ticaretyapanPcTrade	Skipping recipe ID 1791 - not found in game's RecipeData
2025.04.09 01:25:26,498	WARNING	3	quests.Q00802_ticaretyapanPcTrade.Q00802_ticaretyapanPcTrade	Skipping recipe ID 1792 - not found in game's RecipeData

C:\aa\l2faruk\dist\game\data\Recipes.xml
böyle hatalar veriyor ama item var recipe.xml dosyasında ?

<?xml version="1.0" encoding="UTF-8"?>
<!-- Documentation: -->
<!-- - statUse can have HP/MP, but you can use both -->
<!-- - altStatChange and productionRare is optional -->
<!-- - in altStatChange you can use XP/SP/GIM(this works only if AltGameCreation = True) -->
<!-- XP: base XP value -->
<!-- SP: base SP value -->
<!-- GIM: the old function used only the skilllevel to determine how many item is grabbed in each step with this stat changer you can multiple this -->
<list xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:noNamespaceSchemaLocation="xsd/Recipes.xsd">
    <item id="1" recipeId="1666" name="mk_wooden_arrow" craftLevel="1" type="dwarven" successRate="100">
        <ingredient id="1864" count="4" />
        <ingredient id="1869" count="2" />
        <production id="17" count="500" />
        <statUse name="MP" value="30" />
    </item>
    <item id="2" recipeId="1786" name="mk_broad_sword" craftLevel="1" type="dwarven" successRate="100">
        <ingredient id="2005" count="1" />
        <ingredient id="1869" count="18" />
        <ingredient id="1870" count="18" />
        <production id="3" count="1" />
        <statUse name="MP" value="30" />
    </item>
    <item id="3" recipeId="1787" name="mk_willow_staff" craftLevel="1" type="dwarven" successRate="100">
        <ingredient id="2006" count="1" />
        <ingredient id="1869" count="12" />
        <ingredient id="1872" count="24" />
        <ingredient id="1864" count="12" />
        <production id="8" count="1" />
        <statUse name="MP" value="30" />
    </item>
    <item id="4" recipeId="1788" name="mk_bow" craftLevel="1" type="dwarven" successRate="100">
        <ingredient id="2007" count="1" />
        <ingredient id="1869" count="20" />
        <ingredient id="1878" count="4" />
        <ingredient id="1866" count="4" />
        <production id="14" count="1" />
        <statUse name="MP" value="30" />
    </item>
    <item id="5" recipeId="1789" name="mk_cedar_staff" craftLevel="1" type="dwarven" successRate="100">
        <ingredient id="2008" count="2" />
        <ingredient id="1869" count="55" />
        <ingredient id="1872" count="110" />
        <ingredient id="1864" count="55" />
        <production id="9" count="1" />
        <statUse name="MP" value="45" />
    </item>
    <item id="6" recipeId="1790" name="mk_dirk" craftLevel="1" type="dwarven" successRate="100">
        <ingredient id="2009" count="2" />
        <ingredient id="1869" count="80" />
        <ingredient id="1870" count="80" />
        <production id="216" count="1" />
        <statUse name="MP" value="45" />
    </item>
    <item id="7" recipeId="1791" name="mk_brandish" craftLevel="1" type="dwarven" successRate="100">
        <ingredient id="2010" count="2" />
        <ingredient id="1869" count="110" />
        <ingredient id="1870" count="55" />
        <production id="1333" count="1" />
        <statUse name="MP" value="45" />
    </item>
    <item id="8" recipeId="1792" name="mk_short_spear" craftLevel="1" type="dwarven" successRate="100">
        <ingredient id="2011" count="3" />
        <ingredient id="1869" count="220" />
        <ingredient id="1870" count="110" />
        <ingredient id="1866" count="55" />
        <production id="15" count="1" />
        <statUse name="MP" value="60" />
    </item>


neden bu hataları alıyorum itemlerin recipe.xml dosyasında olmasına rağmen.
mesela recipenin üretmesi gerekeb item de var item xml dosyalarında 17 id li item

C:\aa\l2faruk\dist\game\data\stats\items\00000-00099.xml
<?xml version="1.0" encoding="UTF-8"?>
<list xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:noNamespaceSchemaLocation="../../xsd/items.xsd">
    <item id="1" type="Weapon" name="Short Sword">
    <item id="17" type="EtcItem" name="Wooden Arrow">
        <!-- An arrow made of wood. It is an arrow used for a no grade bow. -->
        <set name="icon" val="icon.etc_wooden_quiver_i00" />
        <set name="default_action" val="EQUIP" />
        <set name="etcitem_type" val="ARROW" />
        <set name="bodypart" val="lhand" />
        <set name="immediate_effect" val="true" />
        <set name="material" val="WOOD" />
        <set name="weight" val="6" />
        <set name="price" val="3" />
        <set name="is_stackable" val="true" />
    </item>
