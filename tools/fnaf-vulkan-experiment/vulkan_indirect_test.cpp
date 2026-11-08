// Native Vulkan integration test of the actual FNAF indirect-draw adapter.
// Runs on a software ICD too; not a Xiaomi/Android or full engine benchmark.
#include "fnaf_vulkan_compat.h"
#include <vulkan/vulkan.h>
#include <algorithm>
#include <cassert>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <iterator>
#include <stdexcept>
#include <vector>

static int errors = 0;
static VKAPI_ATTR VkBool32 VKAPI_CALL debugCallback(VkDebugUtilsMessageSeverityFlagBitsEXT severity,
    VkDebugUtilsMessageTypeFlagsEXT, const VkDebugUtilsMessengerCallbackDataEXT* data, void*) {
    if (severity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT) {
        ++errors; std::fprintf(stderr,"Validation ERROR: %s\n", data->pMessage);
    }
    return VK_FALSE;
}
static void ok(VkResult result, const char* what) {
    if (result != VK_SUCCESS) { std::fprintf(stderr,"%s failed (%d)\n",what,result); std::exit(1); }
}
struct Buffer { VkBuffer handle{}; VkDeviceMemory memory{}; };
struct Fixture {
    VkInstance instance{}; VkDebugUtilsMessengerEXT messenger{};
    VkPhysicalDevice physical{}; VkDevice device{}; VkQueue queue{};
    std::uint32_t queueFamily{};
    VkPhysicalDeviceMemoryProperties memoryProperties{};
    VkCommandPool pool{};
    explicit Fixture(bool multi, bool clipDistance = false) {
        const char* layers[] = {"VK_LAYER_KHRONOS_validation"};
        const char* extensions[] = {VK_EXT_DEBUG_UTILS_EXTENSION_NAME};
        VkApplicationInfo app{VK_STRUCTURE_TYPE_APPLICATION_INFO}; app.apiVersion = VK_API_VERSION_1_1;
        VkDebugUtilsMessengerCreateInfoEXT debug{VK_STRUCTURE_TYPE_DEBUG_UTILS_MESSENGER_CREATE_INFO_EXT};
        debug.messageSeverity = VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT | VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT;
        debug.messageType = VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT | VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT | VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT;
        debug.pfnUserCallback = debugCallback;
        VkInstanceCreateInfo ci{VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO}; ci.pApplicationInfo=&app;
        ci.enabledLayerCount=1; ci.ppEnabledLayerNames=layers; ci.enabledExtensionCount=1; ci.ppEnabledExtensionNames=extensions; ci.pNext=&debug;
        ok(vkCreateInstance(&ci,nullptr,&instance),"create instance with validation");
        auto createDebug = reinterpret_cast<PFN_vkCreateDebugUtilsMessengerEXT>(vkGetInstanceProcAddr(instance,"vkCreateDebugUtilsMessengerEXT"));
        if (!createDebug) throw std::runtime_error("missing debug utils");
        ok(createDebug(instance,&debug,nullptr,&messenger),"create debug messenger");
        std::uint32_t n=0; ok(vkEnumeratePhysicalDevices(instance,&n,nullptr),"count devices");
        if (!n) throw std::runtime_error("no Vulkan device");
        std::vector<VkPhysicalDevice> devices(n);ok(vkEnumeratePhysicalDevices(instance,&n,devices.data()),"devices");physical=devices[0];
        VkPhysicalDeviceFeatures avail{};vkGetPhysicalDeviceFeatures(physical,&avail);
        if (multi && !avail.multiDrawIndirect) throw std::runtime_error("device cannot run comparison multi-draw");
        if (clipDistance && !avail.shaderClipDistance) throw std::runtime_error("device cannot run comparison clip distance");
        vkGetPhysicalDeviceMemoryProperties(physical,&memoryProperties);
        vkGetPhysicalDeviceQueueFamilyProperties(physical,&n,nullptr);std::vector<VkQueueFamilyProperties> qs(n);
        vkGetPhysicalDeviceQueueFamilyProperties(physical,&n,qs.data());
        while(queueFamily<n && !(qs[queueFamily].queueFlags & VK_QUEUE_GRAPHICS_BIT)) ++queueFamily;
        if(queueFamily==n) throw std::runtime_error("no graphics queue");
        float priority=1.f; VkDeviceQueueCreateInfo qi{VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO};
        qi.queueFamilyIndex=queueFamily; qi.queueCount=1; qi.pQueuePriorities=&priority;
        VkPhysicalDeviceFeatures enabled{}; enabled.multiDrawIndirect=multi ? VK_TRUE : VK_FALSE;
        enabled.shaderClipDistance=clipDistance ? VK_TRUE : VK_FALSE;
        VkDeviceCreateInfo di{VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO};di.queueCreateInfoCount=1;di.pQueueCreateInfos=&qi;di.pEnabledFeatures=&enabled;
        ok(vkCreateDevice(physical,&di,nullptr,&device),"create device");vkGetDeviceQueue(device,queueFamily,0,&queue);
        VkCommandPoolCreateInfo pi{VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO};pi.queueFamilyIndex=queueFamily;
        ok(vkCreateCommandPool(device,&pi,nullptr,&pool),"create command pool");
    }
    ~Fixture() {
        vkDeviceWaitIdle(device);vkDestroyCommandPool(device,pool,nullptr);vkDestroyDevice(device,nullptr);
        auto destroyDebug=reinterpret_cast<PFN_vkDestroyDebugUtilsMessengerEXT>(vkGetInstanceProcAddr(instance,"vkDestroyDebugUtilsMessengerEXT"));
        if (destroyDebug) destroyDebug(instance, messenger, nullptr);
        vkDestroyInstance(instance, nullptr);
    }
    std::uint32_t memoryType(std::uint32_t bits,VkMemoryPropertyFlags flags) const {
        for(std::uint32_t i=0;i<memoryProperties.memoryTypeCount;i++)
            if((bits & (1u<<i)) && (memoryProperties.memoryTypes[i].propertyFlags & flags)==flags)return i;
        throw std::runtime_error("required memory type missing");
    }
    Buffer buffer(VkDeviceSize size,VkBufferUsageFlags usage,const void* data=nullptr) const {
        Buffer b;VkBufferCreateInfo ci{VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO};ci.size=size;ci.usage=usage;ci.sharingMode=VK_SHARING_MODE_EXCLUSIVE;
        ok(vkCreateBuffer(device,&ci,nullptr,&b.handle),"create buffer");VkMemoryRequirements r{};vkGetBufferMemoryRequirements(device,b.handle,&r);
        VkMemoryAllocateInfo ai{VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};ai.allocationSize=r.size;
        ai.memoryTypeIndex=memoryType(r.memoryTypeBits,VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT|VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        ok(vkAllocateMemory(device,&ai,nullptr,&b.memory),"allocate buffer memory");ok(vkBindBufferMemory(device,b.handle,b.memory,0),"bind buffer memory");
        if(data){void* p=nullptr;ok(vkMapMemory(device,b.memory,0,size,0,&p),"map buffer");std::memcpy(p,data,static_cast<size_t>(size));vkUnmapMemory(device,b.memory);}
        return b;
    }
    void freeBuffer(Buffer b) const {vkDestroyBuffer(device,b.handle,nullptr);vkFreeMemory(device,b.memory,nullptr);}
};
static VkShaderModule shader(VkDevice device, const char* path) {
    std::ifstream f(path,std::ios::binary|std::ios::ate);if(!f)throw std::runtime_error("shader not found");
    auto n=f.tellg();if(n<=0 || n%4)throw std::runtime_error("bad SPIR-V size");f.seekg(0);
    std::vector<std::uint32_t> words(static_cast<size_t>(n)/4);f.read(reinterpret_cast<char*>(words.data()),n);
    VkShaderModuleCreateInfo ci{VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO};ci.codeSize=static_cast<size_t>(n);ci.pCode=words.data();
    VkShaderModule module{};ok(vkCreateShaderModule(device,&ci,nullptr,&module),"create shader");return module;
}
static std::vector<unsigned char> render(bool multi,bool indexed,const char* vertex,const char* fragment, bool clipDistance = false) {
    Fixture f(multi, clipDistance);auto dev=f.device;constexpr std::uint32_t W=96,H=48;
    VkImageCreateInfo ii{VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO};ii.imageType=VK_IMAGE_TYPE_2D;ii.format=VK_FORMAT_R8G8B8A8_UNORM;
    ii.extent={W,H,1};ii.mipLevels=1;ii.arrayLayers=1;ii.samples=VK_SAMPLE_COUNT_1_BIT;ii.tiling=VK_IMAGE_TILING_OPTIMAL;
    ii.usage=VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT|VK_IMAGE_USAGE_TRANSFER_SRC_BIT;ii.initialLayout=VK_IMAGE_LAYOUT_UNDEFINED;
    VkImage image{};ok(vkCreateImage(dev,&ii,nullptr,&image),"create image");VkMemoryRequirements mr{};vkGetImageMemoryRequirements(dev,image,&mr);
    VkMemoryAllocateInfo mai{VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};mai.allocationSize=mr.size;mai.memoryTypeIndex=f.memoryType(mr.memoryTypeBits,VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
    VkDeviceMemory imageMemory{};ok(vkAllocateMemory(dev,&mai,nullptr,&imageMemory),"allocate image memory");ok(vkBindImageMemory(dev,image,imageMemory,0),"bind image");
    VkImageViewCreateInfo vi{VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO};vi.image=image;vi.viewType=VK_IMAGE_VIEW_TYPE_2D;vi.format=ii.format;
    vi.subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};VkImageView view{};ok(vkCreateImageView(dev,&vi,nullptr,&view),"image view");
    VkAttachmentDescription attachment{};attachment.format=ii.format;attachment.samples=VK_SAMPLE_COUNT_1_BIT;
    attachment.loadOp=VK_ATTACHMENT_LOAD_OP_CLEAR;attachment.storeOp=VK_ATTACHMENT_STORE_OP_STORE;
    attachment.stencilLoadOp=VK_ATTACHMENT_LOAD_OP_DONT_CARE;attachment.stencilStoreOp=VK_ATTACHMENT_STORE_OP_DONT_CARE;
    attachment.initialLayout=VK_IMAGE_LAYOUT_UNDEFINED;attachment.finalLayout=VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;
    VkAttachmentReference colorRef{0,VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL};VkSubpassDescription sub{};
    sub.pipelineBindPoint=VK_PIPELINE_BIND_POINT_GRAPHICS;sub.colorAttachmentCount=1;sub.pColorAttachments=&colorRef;
    VkSubpassDependency deps[2]{};deps[0].srcSubpass=VK_SUBPASS_EXTERNAL;deps[0].dstSubpass=0;
    deps[0].srcStageMask=VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;deps[0].dstStageMask=VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
    deps[0].dstAccessMask=VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;deps[1].srcSubpass=0;deps[1].dstSubpass=VK_SUBPASS_EXTERNAL;
    deps[1].srcStageMask=VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;deps[1].dstStageMask=VK_PIPELINE_STAGE_TRANSFER_BIT;
    deps[1].srcAccessMask=VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;deps[1].dstAccessMask=VK_ACCESS_TRANSFER_READ_BIT;
    VkRenderPassCreateInfo ri{VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO};ri.attachmentCount=1;ri.pAttachments=&attachment;ri.subpassCount=1;ri.pSubpasses=&sub;ri.dependencyCount=2;ri.pDependencies=deps;
    VkRenderPass pass{};ok(vkCreateRenderPass(dev,&ri,nullptr,&pass),"render pass");VkFramebufferCreateInfo fi{VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO};
    fi.renderPass=pass;fi.attachmentCount=1;fi.pAttachments=&view;fi.width=W;fi.height=H;fi.layers=1;VkFramebuffer framebuffer{};ok(vkCreateFramebuffer(dev,&fi,nullptr,&framebuffer),"framebuffer");
    auto vs=shader(dev,vertex),fs=shader(dev,fragment);VkPipelineShaderStageCreateInfo stages[2]{};
    stages[0].sType=stages[1].sType=VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;stages[0].stage=VK_SHADER_STAGE_VERTEX_BIT;stages[0].module=vs;stages[0].pName="main";
    stages[1].stage=VK_SHADER_STAGE_FRAGMENT_BIT;stages[1].module=fs;stages[1].pName="main";
    VkPipelineVertexInputStateCreateInfo vert{VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO};
    VkPipelineInputAssemblyStateCreateInfo assembly{VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO};assembly.topology=VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
    VkViewport viewport{0,0,float(W),float(H),0,1};VkRect2D scissor{{0,0},{W,H}};
    VkPipelineViewportStateCreateInfo vp{VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO};vp.viewportCount=1;vp.pViewports=&viewport;vp.scissorCount=1;vp.pScissors=&scissor;
    VkPipelineRasterizationStateCreateInfo rs{VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO};rs.polygonMode=VK_POLYGON_MODE_FILL;rs.cullMode=VK_CULL_MODE_NONE;rs.frontFace=VK_FRONT_FACE_COUNTER_CLOCKWISE;rs.lineWidth=1;
    VkPipelineMultisampleStateCreateInfo ms{VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO};ms.rasterizationSamples=VK_SAMPLE_COUNT_1_BIT;
    VkPipelineColorBlendAttachmentState ca{};ca.colorWriteMask=VK_COLOR_COMPONENT_R_BIT|VK_COLOR_COMPONENT_G_BIT|VK_COLOR_COMPONENT_B_BIT|VK_COLOR_COMPONENT_A_BIT;
    VkPipelineColorBlendStateCreateInfo cb{VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO};cb.attachmentCount=1;cb.pAttachments=&ca;
    VkPipelineLayoutCreateInfo li{VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO};VkPipelineLayout layout{};ok(vkCreatePipelineLayout(dev,&li,nullptr,&layout),"pipeline layout");
    VkGraphicsPipelineCreateInfo gi{VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO};gi.stageCount=2;gi.pStages=stages;gi.pVertexInputState=&vert;
    gi.pInputAssemblyState=&assembly;gi.pViewportState=&vp;gi.pRasterizationState=&rs;gi.pMultisampleState=&ms;gi.pColorBlendState=&cb;gi.layout=layout;gi.renderPass=pass;
    VkPipeline pipeline{};ok(vkCreateGraphicsPipelines(dev,VK_NULL_HANDLE,1,&gi,nullptr,&pipeline),"graphics pipeline");
    VkDrawIndirectCommand direct[3]={{3,1,0,0},{3,1,3,0},{3,1,6,0}};
    VkDrawIndexedIndirectCommand indices[3]={{3,1,0,0,0},{3,1,3,0,0},{3,1,6,0,0}};
    const std::uint16_t indexData[9]={0,1,2,3,4,5,6,7,8};
    std::uint32_t stride=indexed?sizeof(indices[0]):sizeof(direct[0]);
    Buffer commands=f.buffer(stride*3,VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT,indexed?static_cast<void*>(indices):static_cast<void*>(direct));
    Buffer index=f.buffer(sizeof(indexData),VK_BUFFER_USAGE_INDEX_BUFFER_BIT,indexData);
    Buffer readback=f.buffer(W*H*4,VK_BUFFER_USAGE_TRANSFER_DST_BIT);
    VkCommandBufferAllocateInfo ai{VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO};ai.commandPool=f.pool;ai.level=VK_COMMAND_BUFFER_LEVEL_PRIMARY;ai.commandBufferCount=1;
    VkCommandBuffer cmd{};ok(vkAllocateCommandBuffers(dev,&ai,&cmd),"allocate command buffer");VkCommandBufferBeginInfo bi{VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO};ok(vkBeginCommandBuffer(cmd,&bi),"begin");
    VkClearValue clear{};clear.color.float32[3]=1;VkRenderPassBeginInfo begin{VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO};begin.renderPass=pass;begin.framebuffer=framebuffer;
    begin.renderArea={{0,0},{W,H}};begin.clearValueCount=1;begin.pClearValues=&clear;vkCmdBeginRenderPass(cmd,&begin,VK_SUBPASS_CONTENTS_INLINE);
    vkCmdBindPipeline(cmd,VK_PIPELINE_BIND_POINT_GRAPHICS,pipeline);if(indexed)vkCmdBindIndexBuffer(cmd,index.handle,0,VK_INDEX_TYPE_UINT16);
    unsigned actualCalls=0;
    FnafVulkanCompat::IndirectDraws(0,3,stride,multi,[&](std::uint64_t offset,std::uint32_t count,std::uint32_t step) {
        ++actualCalls;if(indexed)vkCmdDrawIndexedIndirect(cmd,commands.handle,offset,count,step);else vkCmdDrawIndirect(cmd,commands.handle,offset,count,step);
    });
    if(actualCalls!=(multi?1u:3u))throw std::runtime_error("bad draw count");
    vkCmdEndRenderPass(cmd);VkBufferImageCopy copy{};copy.imageSubresource={VK_IMAGE_ASPECT_COLOR_BIT,0,0,1};copy.imageExtent={W,H,1};
    vkCmdCopyImageToBuffer(cmd,image,VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,readback.handle,1,&copy);ok(vkEndCommandBuffer(cmd),"end");
    VkSubmitInfo submit{VK_STRUCTURE_TYPE_SUBMIT_INFO};submit.commandBufferCount=1;submit.pCommandBuffers=&cmd;ok(vkQueueSubmit(f.queue,1,&submit,VK_NULL_HANDLE),"submit");ok(vkQueueWaitIdle(f.queue),"wait");
    void* mapped=nullptr;ok(vkMapMemory(dev,readback.memory,0,W*H*4,0,&mapped),"map readback");
    auto* ptr=static_cast<unsigned char*>(mapped);std::vector<unsigned char> pixels(ptr,ptr+W*H*4);vkUnmapMemory(dev,readback.memory);
    std::size_t red=0,green=0,blue=0;for(std::size_t i=0;i<pixels.size();i+=4){red+=pixels[i]>200;green+=pixels[i+1]>200;blue+=pixels[i+2]>200;}
    if(red<100||green<100||blue<100)throw std::runtime_error("some triangles missing");
    f.freeBuffer(readback);f.freeBuffer(index);f.freeBuffer(commands);vkDestroyPipeline(dev,pipeline,nullptr);vkDestroyPipelineLayout(dev,layout,nullptr);
    vkDestroyShaderModule(dev,fs,nullptr);vkDestroyShaderModule(dev,vs,nullptr);vkDestroyFramebuffer(dev,framebuffer,nullptr);vkDestroyRenderPass(dev,pass,nullptr);
    vkDestroyImageView(dev,view,nullptr);vkDestroyImage(dev,image,nullptr);vkFreeMemory(dev,imageMemory,nullptr);
    std::printf("PASS native Vulkan %s draw with multiDrawIndirect=%d: R=%zu G=%zu B=%zu; calls=%u\n",indexed?"indexed":"non-indexed",int(multi),red,green,blue,actualCalls);
    return pixels;
}
int main(int argc,char** argv) {
    if(argc!=3){std::fprintf(stderr,"usage: vulkan_indirect_test vertex.spv fragment.spv\n");return 2;}
    try {
        auto split=render(false,false,argv[1],argv[2]), multi=render(true,false,argv[1],argv[2]);
        auto indexedSplit=render(false,true,argv[1],argv[2]), indexedMulti=render(true,true,argv[1],argv[2]);
        if(split!=multi || split!=indexedSplit || split!=indexedMulti)throw std::runtime_error("pixel mismatch");
        if(errors)throw std::runtime_error("Vulkan validation errors occurred");
        std::puts("PASS pixel-identical output and zero Vulkan validation errors across all four paths");
    } catch(const std::exception& e) {std::fprintf(stderr,"FAIL: %s\n",e.what());return 1;}
    return 0;
}
