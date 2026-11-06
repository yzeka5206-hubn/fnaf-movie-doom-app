#include "fnaf_outside_black.h"
#include <iostream>
#include <string>
int main() {
    char kind; std::string map; int src,dst; double sx,sy,dx,dy;
    while (std::cin >> kind >> map) {
        if (kind == 'p') {
            std::cin >> src >> dst >> sx >> sy >> dx >> dy;
            std::cout << FnafOutsideBlack::ExteriorPortal(map.c_str(),src,dst,sx,sy,dx,dy) << '\n';
        } else if (kind == 'v') {
            std::cin >> sx >> sy;
            std::cout << FnafOutsideBlack::ExteriorView(map.c_str(),sx,sy) << '\n';
        } else return 2;
    }
}
