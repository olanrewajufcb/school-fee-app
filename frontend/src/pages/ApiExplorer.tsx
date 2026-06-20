import React, { useMemo, useEffect } from 'react';
import Sidebar from '@/components/Sidebar';
import TopBar from '@/components/TopBar';
import DocContent from '@/components/DocContent';
import TryItPanel from '@/components/TryItPanel';
import { getAllEndpoints } from '@/utils/openapiParser';
import { useStore } from '@/store/useStore';

const ApiExplorer: React.FC = () => {
  const { controllers, endpoints } = useMemo(() => getAllEndpoints(), []);
  
  const { 
    activeEndpointId, 
    setActiveEndpoint, 
    setActiveController,
    theme
  } = useStore();

  const activeEndpoint = useMemo(() => {
    return endpoints.find(e => e.id === activeEndpointId) || null;
  }, [endpoints, activeEndpointId]);

  // Set default active endpoint on mount if none selected
  useEffect(() => {
    if (!activeEndpointId && controllers.length > 0 && controllers[0].endpoints.length > 0) {
      setActiveController(controllers[0].name);
      setActiveEndpoint(controllers[0].endpoints[0].id);
    }
  }, [activeEndpointId, controllers, setActiveController, setActiveEndpoint]);

  // Handle scroll-sync logic
  useEffect(() => {
    const handleScroll = () => {
      // Find the endpoint currently in view
      const endpointElements = document.querySelectorAll('[id^="endpoint-"]');
      let currentActiveId = '';
      
      for (const el of endpointElements) {
        const rect = el.getBoundingClientRect();
        if (rect.top <= 150 && rect.bottom >= 150) {
          currentActiveId = el.id.replace('endpoint-', '');
          break;
        }
      }

      if (currentActiveId && currentActiveId !== activeEndpointId) {
        const ep = endpoints.find(e => e.id === currentActiveId);
        if (ep) {
          setActiveEndpoint(ep.id);
          setActiveController(ep.controller);
        }
      }
    };

    const mainContent = document.getElementById('api-docs-main');
    if (mainContent) {
      mainContent.addEventListener('scroll', handleScroll);
      return () => mainContent.removeEventListener('scroll', handleScroll);
    }
  }, [endpoints, activeEndpointId, setActiveEndpoint, setActiveController]);

  return (
    <div className={`flex flex-col h-screen overflow-hidden ${theme === 'dark' ? 'dark bg-slate-950' : 'bg-slate-50'}`}>
      <TopBar />
      
      <div className="flex flex-1 overflow-hidden relative mt-12">
        <Sidebar controllers={controllers} />
        
        <main 
          id="api-docs-main"
          className="flex-1 overflow-y-auto scroll-smooth w-full lg:ml-64 transition-all duration-300"
        >
          <div className="max-w-[1200px] mx-auto pb-32">
            <DocContent endpoints={endpoints} />
          </div>
        </main>
        
        <TryItPanel endpoint={activeEndpoint} />
      </div>
    </div>
  );
};

export default ApiExplorer;
