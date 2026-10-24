package com.ecommerce.productservice.service.impl;

import com.ecommerce.productservice.dto.request.CategoryRequest;
import com.ecommerce.productservice.dto.response.CategoryResponse;
import com.ecommerce.productservice.dto.response.CategoryTreeNode;
import com.ecommerce.productservice.entity.Category;
import com.ecommerce.productservice.exception.BadRequestException;
import com.ecommerce.productservice.exception.ConflictException;
import com.ecommerce.productservice.exception.ResourceNotFoundException;
import com.ecommerce.productservice.outbox.ProductEventPublisher;
import com.ecommerce.productservice.repository.CategoryRepository;
import com.ecommerce.productservice.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductEventPublisher eventPublisher;
    @InjectMocks
    private CategoryServiceImpl categoryService;

    // Electronics > (Mobiles > Smartphones, Laptops) ; Books
    private Category electronics;
    private Category mobiles;
    private Category smartphones;
    private Category laptops;
    private Category books;

    @BeforeEach
    void setUp() {
        electronics = category("Electronics", null);
        mobiles = category("Mobiles", electronics);
        smartphones = category("Smartphones", mobiles);
        laptops = category("Laptops", electronics);
        books = category("Books", null);
    }

    private static Category category(String name, Category parent) {
        return Category.builder().id(UUID.randomUUID()).name(name).parent(parent).build();
    }

    private List<Category> all() {
        return List.of(electronics, mobiles, smartphones, laptops, books);
    }

    // ---------- Tree ----------

    @Test
    void getTree_nestsChildrenAndSortsByName() {
        when(categoryRepository.findAll()).thenReturn(all());

        List<CategoryTreeNode> tree = categoryService.getTree();

        assertThat(tree).extracting(CategoryTreeNode::name).containsExactly("Books", "Electronics");
        CategoryTreeNode electronicsNode = tree.get(1);
        assertThat(electronicsNode.children()).extracting(CategoryTreeNode::name).containsExactly("Laptops", "Mobiles");
        assertThat(electronicsNode.children().get(1).children()).extracting(CategoryTreeNode::name)
                .containsExactly("Smartphones");
    }

    @Test
    void getSelfAndDescendantIds_includesAllLevels() {
        when(categoryRepository.findAll()).thenReturn(all());

        Set<UUID> ids = categoryService.getSelfAndDescendantIds(electronics.getId());

        assertThat(ids).containsExactlyInAnyOrder(
                electronics.getId(), mobiles.getId(), smartphones.getId(), laptops.getId());
    }

    @Test
    void getSelfAndDescendantIds_unknownCategory_isEmpty() {
        when(categoryRepository.findAll()).thenReturn(all());

        assertThat(categoryService.getSelfAndDescendantIds(UUID.randomUUID())).isEmpty();
    }

    @Test
    void getCategory_unknown_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(categoryRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.getCategory(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- Create ----------

    @Test
    void create_topLevel_trimsAndSaves() {
        when(categoryRepository.existsByParentIsNullAndNameIgnoreCase("Fashion")).thenReturn(false);
        when(categoryRepository.saveAndFlush(any(Category.class))).thenAnswer(inv -> {
            Category c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        CategoryResponse response = categoryService.create(new CategoryRequest("  Fashion ", "  ", null));

        assertThat(response.name()).isEqualTo("Fashion");
        assertThat(response.description()).isNull();
        assertThat(response.parent()).isNull();
    }

    @Test
    void create_child_underExistingParent() {
        when(categoryRepository.findById(electronics.getId())).thenReturn(Optional.of(electronics));
        when(categoryRepository.existsByParent_IdAndNameIgnoreCase(electronics.getId(), "Cameras")).thenReturn(false);
        when(categoryRepository.saveAndFlush(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        CategoryResponse response = categoryService.create(new CategoryRequest("Cameras", null, electronics.getId()));

        assertThat(response.parent().name()).isEqualTo("Electronics");
    }

    @Test
    void create_duplicateSiblingName_throwsConflict() {
        when(categoryRepository.existsByParentIsNullAndNameIgnoreCase("Books")).thenReturn(true);

        assertThatThrownBy(() -> categoryService.create(new CategoryRequest("Books", null, null)))
                .isInstanceOf(ConflictException.class);
        verify(categoryRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_unknownParent_throwsBadRequest() {
        UUID parentId = UUID.randomUUID();
        when(categoryRepository.findById(parentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.create(new CategoryRequest("Cameras", null, parentId)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void create_concurrentDuplicateCaughtByIndex_throwsConflict() {
        when(categoryRepository.existsByParentIsNullAndNameIgnoreCase("Toys")).thenReturn(false);
        when(categoryRepository.saveAndFlush(any(Category.class)))
                .thenThrow(new DataIntegrityViolationException("uk_categories_root_name"));

        assertThatThrownBy(() -> categoryService.create(new CategoryRequest("Toys", null, null)))
                .isInstanceOf(ConflictException.class);
    }

    // ---------- Update ----------

    @Test
    void update_movingUnderOwnDescendant_throwsBadRequest() {
        when(categoryRepository.findById(electronics.getId())).thenReturn(Optional.of(electronics));
        when(categoryRepository.findAll()).thenReturn(all());

        assertThatThrownBy(() -> categoryService.update(electronics.getId(),
                new CategoryRequest("Electronics", null, smartphones.getId())))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("cannot be moved under itself");
    }

    @Test
    void update_movingUnderItself_throwsBadRequest() {
        when(categoryRepository.findById(mobiles.getId())).thenReturn(Optional.of(mobiles));
        when(categoryRepository.findAll()).thenReturn(all());

        assertThatThrownBy(() -> categoryService.update(mobiles.getId(),
                new CategoryRequest("Mobiles", null, mobiles.getId())))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void update_moveToAnotherBranch_succeeds() {
        when(categoryRepository.findById(laptops.getId())).thenReturn(Optional.of(laptops));
        when(categoryRepository.findById(books.getId())).thenReturn(Optional.of(books));
        when(categoryRepository.findAll()).thenReturn(all());
        when(categoryRepository.existsByParent_IdAndNameIgnoreCaseAndIdNot(books.getId(), "Laptops", laptops.getId()))
                .thenReturn(false);
        when(categoryRepository.saveAndFlush(laptops)).thenReturn(laptops);

        CategoryResponse response = categoryService.update(laptops.getId(),
                new CategoryRequest("Laptops", null, books.getId()));

        assertThat(response.parent().id()).isEqualTo(books.getId());
    }

    // ---------- Delete ----------

    @Test
    void delete_withSubcategories_throwsConflict() {
        when(categoryRepository.findById(electronics.getId())).thenReturn(Optional.of(electronics));
        when(categoryRepository.existsByParent_Id(electronics.getId())).thenReturn(true);

        assertThatThrownBy(() -> categoryService.delete(electronics.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("sub-categories");
        verify(categoryRepository, never()).delete(any());
    }

    @Test
    void delete_withProducts_throwsConflict() {
        when(categoryRepository.findById(laptops.getId())).thenReturn(Optional.of(laptops));
        when(categoryRepository.existsByParent_Id(laptops.getId())).thenReturn(false);
        when(productRepository.existsByCategory_Id(laptops.getId())).thenReturn(true);

        assertThatThrownBy(() -> categoryService.delete(laptops.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("products");
    }

    @Test
    void delete_emptyLeaf_deletes() {
        when(categoryRepository.findById(books.getId())).thenReturn(Optional.of(books));
        when(categoryRepository.existsByParent_Id(books.getId())).thenReturn(false);
        when(productRepository.existsByCategory_Id(books.getId())).thenReturn(false);

        categoryService.delete(books.getId());

        verify(categoryRepository).delete(books);
    }
}
