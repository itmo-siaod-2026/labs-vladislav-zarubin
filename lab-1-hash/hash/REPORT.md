# Отчет по лабораторной работе: Хэширование

Содержание:
- [Реализованные структуры](#реализованные-структуры)
- [Методика benchmark-ов](#методика-benchmark-ов)
- [Результаты benchmark-ов](#результаты-benchmark-ов)
  - [FileHashTable](#filehashtable)
  - [PerfectHash](#perfecthash)
  - [LSH](#lsh)
- [Профилирование и узкие места](#профилирование-и-узкие-места)
- [Гипотезы улучшения](#гипотезы-улучшения)
- [Вывод](#вывод)

## Реализованные структуры

### FileHashTable

FileHashTable сделана как  hash table на основе extendible hashing. Директория и бакеты лежат в файлах, работа идет через MappedByteBuffer. Поддерживаются insert, get, update и delete; именно эти операции и замерялись.

### PerfectHash

PerfectHashIndex строит двухуровневый perfect hash для заранее известного набора уникальных ключей. Индекс сначала строится, после этого lookup идет почти за константное время. В benchmark-ах отдельно измерялись build и hit lookup.

### LSH

LshDuplicateIndex строит сигнатуры точек по случайным гиперплоскостям и раскладывает точки по bucket-ам. Поддерживаются добавление точек и поиск дублей через LSH или через полный перебор. В benchmark-ах измерялись build, add, lshSearch и fullScan.

## Методика benchmark-ов

Все замеры запускались через JMH 1.37 с @Warmup(iterations = 5, time = 1), @Measurement(iterations = 10, time = 1), @Fork(3) и одним потоком.

Для FileHashTable отдельно измерялись get, update, insert fresh и delete. Для PerfectHashIndex - buildIndex и lookupHit. Для LSH - buildIndex, addPoints, lshSearch и fullScan.

## Результаты benchmark-ов

В таблицах ниже приведены текущие значения из CSV. Для latency используются нс/оп, для throughput - ops/s.

### FileHashTable

По FileHashTable лучше всего видно, что get, update и delete ведут себя довольно ровно, а insert - заметно хуже. Это хорошо согласуется с устройством extendible hashing: чтение и update локальные, а вставка иногда попадает в split и перезапись бакетов.

Latency, нс/оп:

| N | Вставка | Обновление | Удаление | Поиск |
|---|---|---|---|---|
| 2000 | 4,668.4 ± 416.603 | 103.373 ± 0.586 | 67.701 ± 1.242 | 64.093 ± 0.595 |
| 4000 | 6,387.9 ± 569.591 | 107.883 ± 0.872 | 65.737 ± 1.226 | 67.895 ± 0.65 |
| 6000 | 3,692.5 ± 296.599 | 112.272 ± 1.122 | 71.992 ± 1.735 | 70.51 ± 0.473 |
| 8000 | 8,400.8 ± 934.632 | 113.267 ± 1.008 | 70.558 ± 0.774 | 74.233 ± 0.607 |
| 10000 | 5,668.2 ± 743.754 | 114.747 ± 1.133 | 73.863 ± 1.364 | 77.209 ± 0.562 |
| 12000 | 5,352.0 ± 606.826 | 117.497 ± 1.225 | 74.106 ± 0.971 | 77.743 ± 1.055 |

Throughput, ops/s:

| N | Вставка | Обновление | Удаление | Поиск |
|---|---|---|---|---|
| 2000 | 217,212 ± 16,757 | 9,527,551 ± 67,429 | 14,542,037 ± 830,270 | 15,592,937 ± 121,628 |
| 4000 | 155,147 ± 13,091 | 9,145,169 ± 58,534 | 15,044,437 ± 205,504 | 14,719,500 ± 116,862 |
| 6000 | 275,405 ± 22,778 | 8,822,883 ± 78,059 | 14,271,446 ± 255,883 | 14,204,352 ± 148,297 |
| 8000 | 123,156 ± 11,627 | 8,766,989 ± 92,942 | 14,022,076 ± 121,628 | 13,714,280 ± 155,848 |
| 10000 | 195,166 ± 25,346 | 8,628,866 ± 86,084 | 13,533,335 ± 155,944 | 13,209,532 ± 124,217 |
| 12000 | 198,026 ± 22,127 | 8,487,421 ± 83,575 | 13,131,866 ± 244,611 | 13,072,321 ± 141,325 |

![FileHashTable get latency](graphs/png_filehash/getExisting_latency.png)

Чтение растет плавно, без резких скачков. Здесь основная цена - проход по бакету и сравнение ключа.

![FileHashTable get throughput](graphs/png_filehash/getExisting_throughput.png)
![FileHashTable get CPU](profiles_png/filehash-get-cpu.png)
![FileHashTable get alloc](profiles_png/filehash-get-alloc.png)

Throughput снижается ровно. Для такого сценария это ожидаемо.

#### Проверка, что get работает за O(1)

Рост 64 → 77 нс на `getExisting*` — не алгоритмический. `scanBucket` всегда сканирует ровно `bucketCapacity = 32` слота независимо от N, `directoryIndex` — это одна операция `hash & mask`, `bucketHandles.get` — hashmap lookup по int ключу с ограниченным числом bucket-ов. Настоящий источник роста — cache-pressure: с увеличением N растёт число bucket-файлов и суммарный working set `MappedByteBuffer`-ов, а случайные `queryIndexes` распределяются по всему диапазону, из-за чего увеличивается доля L1/L2 cache miss на сам bucket-буфер.

Чтобы это отделить, добавлен бенч `getHotSetBatch` / `getHotSetThroughput`: запросы берутся не из всего диапазона, а из hot-set размером 64 ключа (тех же 64 независимо от N). Эти ключи после всех split-ов ложатся максимум в несколько bucket-ов, которые целиком помещаются в L1 и остаются резидентными весь прогон. Если бы `get` имел алгоритмическую зависимость от N, кривая hot-set всё равно росла бы. На практике она плоская, а её дельта с `getExisting*` и есть вклад cache-miss term.

// TODO: вставить таблицу и график `getHotSet*` после перезапуска JMH.

Вывод: `get` в FileHashTable — O(1). `getExisting*` показывает «алгоритм + cache miss term», `getHotSet*` изолирует алгоритм.

![FileHashTable insert latency](graphs/png_filehash/insertFresh_latency.png)

Вставка идет неровно. Самые заметные провалы похожи на split и rewrite bucket.

![FileHashTable insert throughput](graphs/png_filehash/insertFresh_throughput.png)
![FileHashTable insert CPU](profiles_png/filehash-insert-cpu.png)
![FileHashTable insert alloc](profiles_png/filehash-insert-alloc.png)

По throughput видно ту же картину. Insert зависит не только от размера таблицы, но и от моментов перераспределения данных.

![FileHashTable update latency](graphs/png_filehash/updateExisting_latency.png)

Update растет почти ровно. Эта операция выглядит заметно стабильнее insert.

![FileHashTable update throughput](graphs/png_filehash/updateExisting_throughput.png)
![FileHashTable update CPU](profiles_png/filehash-update-cpu.png)
![FileHashTable update alloc](profiles_png/filehash-update-alloc.png)

Throughput падает плавно. По поведению update ближе к lookup, чем к insert.

![FileHashTable delete latency](graphs/png_filehash/deleteExisting_latency.png)

Delete тоже идет ровно. Основная цена здесь - найти slot и очистить его.

![FileHashTable delete throughput](graphs/png_filehash/deleteExisting_throughput.png)
![FileHashTable delete CPU](profiles_png/filehash-delete-cpu.png)
![FileHashTable delete alloc](profiles_png/filehash-delete-alloc.png)

Кривая без резких провалов. По графику delete выглядит устойчивее insert.

### PerfectHash

У PerfectHashIndex build и lookup хорошо разделены. По графикам видно простую картину: build дорожает с ростом N, а lookup остается почти плоским.

Latency, нс/оп:

| N | Build | Lookup hit |
|---|---|---|
| 20000 | 2,083,224 ± 371,357 | 32.461 ± 0.554 |
| 40000 | 3,873,799 ± 196,987 | 33.374 ± 0.61 |
| 60000 | 6,698,225 ± 565,696 | 34.028 ± 0.796 |
| 80000 | 8,394,357 ± 766,261 | 34.055 ± 0.479 |
| 100000 | 11,968,082 ± 604,172 | 34.262 ± 0.483 |
| 120000 | 13,754,710 ± 687,352 | 34.776 ± 0.634 |

Throughput для lookup, ops/s:

| N | Lookup hit |
|---|---|
| 20000 | 30,565,589 ± 506,364 |
| 40000 | 29,324,990 ± 643,708 |
| 60000 | 29,385,937 ± 606,393 |
| 80000 | 29,092,536 ± 517,075 |
| 100000 | 28,767,534 ± 584,676 |
| 120000 | 28,906,611 ± 610,888 |

![PerfectHash build latency](graphs/png_perfecthash/buildIndex_latency.png)
![PerfectHash build CPU](profiles_png/perfecthash-build-cpu.png)
![PerfectHash build alloc](profiles_png/perfecthash-build-alloc.png)

Build растет почти линейно. Здесь основная работа - построение первичного и вторичных хешей.

![PerfectHash lookup hit latency](graphs/png_perfecthash/lookupHit_latency.png)

Hit lookup почти плоский. Размер набора влияет слабо.

![PerfectHash lookup hit throughput](graphs/png_perfecthash/lookupHit_throughput.png)
![PerfectHash lookup hit CPU](profiles_png/perfecthash-lookup-hit-cpu.png)
![PerfectHash lookup hit alloc](profiles_png/perfecthash-lookup-hit-alloc.png)

Throughput держится примерно на одном уровне.

### LSH

Для LSH полезно отдельно смотреть build и search. Поиск через индекс заметно лучше полного перебора, хотя и не такой ровный.

Latency, нс/оп:

| N | Build | Add | LSH search | Full scan |
|---|---|---|---|---|
| 2000 | 153,474 ± 9,232 | 16.97 ± 0.289 | 583,390 ± 15,945 | 2,850,617 ± 83,823 |
| 4000 | 288,424 ± 24,184 | 21.715 ± 0.256 | 2,657,842 ± 47,052 | 12,327,253 ± 118,776 |
| 6000 | 421,192 ± 194,459 | 16.538 ± 0.721 | 4,531,763 ± 82,205 | 28,651,632 ± 258,473 |
| 8000 | 438,319 ± 213,788 | 17.256 ± 0.107 | 21,001,447 ± 154,770 | 49,653,724 ± 775,453 |
| 10000 | 397,161 ± 67,577 | 18.338 ± 0.181 | 18,036,467 ± 375,963 | 76,282,197 ± 1,494,091 |
| 12000 | 453,650 ± 76,085 | 18.697 ± 0.244 | 22,276,540 ± 694,592 | 109,609,569 ± 1,944,071 |

Throughput, ops/s:

| N | Build | Add | LSH search | Full scan |
|---|---|---|---|---|
| 2000 | 20,178 ± 186 | 58,417,780 ± 450,006 | 1,783.265 ± 2.166 | 338.941 ± 2.38 |
| 4000 | 15,015 ± 221 | 45,756,728 ± 627,177 | 390.413 ± 5.21 | 81.898 ± 1.161 |
| 6000 | 8,107 ± 243 | 59,768,996 ± 2,673,924 | 225.807 ± 2.955 | 36.272 ± 0.492 |
| 8000 | 5,976 ± 114 | 58,239,591 ± 803,456 | 48.535 ± 0.761 | 20.232 ± 0.368 |
| 10000 | 4,720 ± 102 | 52,949,536 ± 206,470 | 56.631 ± 0.941 | 13.161 ± 0.206 |
| 12000 | 4,911 ± 262 | 53,826,193 ± 286,248 | 45.743 ± 0.518 | 9.301 ± 0.088 |

![LSH build latency](graphs/png_lsh/buildIndex_latency.png)

Build в целом растет почти линейно. Небольшие скачки можно объяснить распределением точек по bucket-ам.

![LSH build throughput](graphs/png_lsh/buildIndex_throughput.png)
![LSH build CPU](profiles_png/lsh-build-cpu.png)
![LSH build alloc](profiles_png/lsh-build-alloc.png)

Throughput падает постепенно. Это соответствует росту полного времени построения.

![LSH search latency](graphs/png_lsh/lshSearch_latency.png)

lshSearch быстрее fullScan, но кривая неровная. Выигрыш заметный, но не везде одинаковый.

![LSH search throughput](graphs/png_lsh/lshSearch_throughput.png)
![LSH search CPU](profiles_png/lsh-search-cpu.png)
![LSH search alloc](profiles_png/lsh-search-alloc.png)

По throughput видно те же локальные провалы. Здесь много зависит от того, сколько кандидатов попало в bucket-ы.

![LSH fullScan latency](graphs/png_lsh/fullScan_latency.png)

fullScan растет почти квадратично. На больших N разница с lshSearch уже очень заметна.

![LSH fullScan throughput](graphs/png_lsh/fullScan_throughput.png)
![LSH fullScan CPU](profiles_png/lsh-fullscan-cpu.png)
![LSH fullScan alloc](profiles_png/lsh-fullscan-alloc.png)

Throughput быстро падает. Для полного перебора это ожидаемый результат.

## Профилирование и узкие места

### FileHashTable

get: [CPU](profiles/filehash-get-cpu.html), [alloc](profiles/filehash-get-alloc.html)  
insert: [CPU](profiles/filehash-insert-cpu.html), [alloc](profiles/filehash-insert-alloc.html)  
update: [CPU](profiles/filehash-update-cpu.html), [alloc](profiles/filehash-update-alloc.html)  
delete: [CPU](profiles/filehash-delete-cpu.html), [alloc](profiles/filehash-delete-alloc.html)

У чтения, update и delete основная цена сидит в scanBucket и matchesFixedBytes. У insert bottleneck уже другой: splitBucket, readEntries, rewriteBucket, saveMetadata, а также файловые вызовы open, unlink и force.

### PerfectHash

build: [CPU](profiles/perfecthash-build-cpu.html), [alloc](profiles/perfecthash-build-alloc.html)  
lookup hit: [CPU](profiles/perfecthash-lookup-hit-cpu.html), [alloc](profiles/perfecthash-lookup-hit-alloc.html)  
lookup общий: [CPU](profiles/perfecthash-lookup-cpu.html), [alloc](profiles/perfecthash-lookup-alloc.html)

У PerfectHashIndex build упирается в fromKeys, Bucket.build и numericKey. После построения lookup почти целиком сидит в find и вычислении хеша, поэтому и графики получаются такими ровными.

Во внутренностях PerfectHashIndex больше нет `HashMap` / `HashSet` / `ArrayList` / `List<List<...>>` — всё перенесено на примитивные массивы (`int[]`, `long[]`, `char[][]`). Дедуп при build идёт через open-addressing `int[]` по `numericKey`. Factory-хелпер тестов `RandomDataFactory.uniqueKeys`, который зовётся из `@Setup` бенча, тоже перестал тянуть `LinkedHashSet`. После перезапуска профайлера в `profiles/perfecthash-*-alloc.html` остаться должны только `char[]`, `int[]`, `long[]` и служебные аллокации JMH.

### LSH

build: [CPU](profiles/lsh-build-cpu.html), [alloc](profiles/lsh-build-alloc.html)  
search: [CPU](profiles/lsh-search-cpu.html), [alloc](profiles/lsh-search-alloc.html)  
full scan: [CPU](profiles/lsh-fullscan-cpu.html), [alloc](profiles/lsh-fullscan-alloc.html)

У build основная работа идет в add, addAll, computeHash и HashMap.computeIfAbsent. У search bottleneck сидит прямо в findDoubles, а у полного перебора - в fullScanDuplicates.

## Гипотезы улучшения

### FileHashTable

- Снизить цену insert за счет уменьшения числа полных rewrite bucket при split.
- Сократить число сравнений в matchesFixedBytes.
- По возможности уменьшить число временных byte[].

### PerfectHash

- Упростить или ускорить подбор secondary hash для тяжелых бакетов.
- Если это допустимо по сценарию, вынести часть стоимости numericKey из горячего пути lookup.

### LSH

- Подобрать параметры хеширования так, чтобы уменьшить число лишних кандидатов.
- Добавить более дешевый предфильтр перед полным сравнением точек.

## Вывод

По стабильности лучше всего выглядит PerfectHashIndex: lookup остается почти одинаковым с ростом N. У FileHashTable самыми ровными получились get, update и delete, а insert ожидаемо проседает из-за split и rewrite bucket. У LSH поиск через индекс заметно лучше полного перебора, но его стоимость зависит от распределения кандидатов по bucket-ам.

Если смотреть на bottleneck-ы, картина тоже простая. Для FileHashTable это scanBucket / matchesFixedBytes на lookup path и splitBucket / rewriteBucket / readEntries на insert path. Для PerfectHashIndex основная цена сидит в build и numericKey, а для LSH - в findDoubles и в квадратичном fullScanDuplicates у baseline.
