#include <new>
#include <cstdio>
#include <cstdlib>


void* operator new(std::size_t size) {
    // std::cout << "Allocating " << size << " bytes" << std::endl;
    printf("operator new %ld\n", size);
    
    // Use malloc here to avoid recursion (calling new from within new)
    void* ptr = std::malloc(size);
    if (!ptr) throw std::bad_alloc(); // Handle allocation failure
    return ptr;
}

void* operator new[](std::size_t size) {
    // std::cout << "Allocating " << size << " bytes" << std::endl;
    printf("operator new[] %ld\n", size);
    
    // Use malloc here to avoid recursion (calling new from within new)
    void* ptr = std::malloc(size);
    if (!ptr) throw std::bad_alloc(); // Handle allocation failure
    return ptr;
}

void operator delete(void* ptr) {
    // std::cout << "Deallocating " << ptr << std::endl;
    std::free(ptr);
}

void operator delete[](void* ptr) {
    // std::cout << "Deallocating " << ptr << std::endl;
    std::free(ptr);
}