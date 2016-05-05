

set xrange [0:10000]
set yrange[-10:400]
#plot '< tail -n 500 graphOne.txt' with lines
#plot 'graphOne.txt' with lines

plot '< tail -n 2000 graphOne.txt graphOne.txt' using 1:2 with lines
pause 0.5
#set xtics 20000
reread
